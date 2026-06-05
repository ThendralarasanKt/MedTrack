package com.medtrack.app.ai

import com.medtrack.app.ai.openrouter.OpenRouterAiClient
import com.medtrack.app.ai.openrouter.OpenRouterChatRequest
import com.medtrack.app.ai.openrouter.OpenRouterClientResult
import com.medtrack.app.ai.openrouter.OpenRouterMessage
import com.medtrack.app.ai.openrouter.OpenRouterProviderOptions
import com.medtrack.app.ai.openrouter.OpenRouterToolCall
import com.medtrack.app.ai.openrouter.toOpenRouterTools
import com.medtrack.app.mcp.MedTrackMcpCatalog
import com.medtrack.app.mcp.client.InAppMcpClient
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject

@Singleton
class AiAssistantOrchestrator @Inject constructor(
    private val openRouterAiClient: OpenRouterAiClient,
    private val mcpClient: InAppMcpClient
) {
    suspend fun handleUserMessage(message: String): String {
        val shouldRequireToolCall = message.shouldRequireMedTrackToolCall()
        val tools = if (message.shouldUseMedTrackTools()) {
            mcpClient.listTools()
            MedTrackMcpCatalog.tools.toOpenRouterTools()
        } else {
            emptyList()
        }
        val request = OpenRouterChatRequest(
            messages = listOf(
                OpenRouterMessage.system(
                    if (tools.isEmpty()) {
                        "You are MedTrack assistant. Answer briefly and helpfully."
                    } else {
                        """
                        You are MedTrack assistant. Use the provided tools when the user asks to read or change MedTrack data.
                        For creating a new patient visit, call create_patient_visit. A visit needs patientId or patientName, roomNumber, and at least one of symptoms or diagnosis.
                        If the user provided enough details for a data change, call the tool instead of describing what should be done.
                        If required details are missing, ask for only the missing details.
                        """.trimIndent()
                    }
                ),
                OpenRouterMessage.user(message)
            ),
            tools = tools,
            toolChoice = if (tools.isNotEmpty() && shouldRequireToolCall) "required" else "auto",
            provider = OpenRouterProviderOptions(requireParameters = tools.isNotEmpty())
        )

        return when (val result = openRouterAiClient.createChatCompletion(request)) {
            is OpenRouterClientResult.Success -> {
                val assistantMessage = result.response.message
                    ?: return "OpenRouter returned an empty response."

                if (assistantMessage.hasToolCalls) {
                    val toolResultMessages = executeToolCalls(assistantMessage.toolCalls)
                    val finalRequest = OpenRouterChatRequest(
                        messages = request.messages +
                            OpenRouterMessage(
                                role = "assistant",
                                content = assistantMessage.content,
                                toolCalls = assistantMessage.toolCalls
                            ) +
                            toolResultMessages,
                        tools = tools,
                        toolChoice = "auto",
                        provider = OpenRouterProviderOptions(requireParameters = tools.isNotEmpty())
                    )

                    when (val finalResult = openRouterAiClient.createChatCompletion(finalRequest)) {
                        is OpenRouterClientResult.Success ->
                            finalResult.response.message?.content?.takeIf { it.isNotBlank() }
                                ?: toolResultMessages.joinToString(separator = "\n") { it.content.orEmpty() }
                        is OpenRouterClientResult.Error ->
                            toolResultMessages.joinToString(separator = "\n") { it.content.orEmpty() }
                    }
                } else {
                    assistantMessage.content.takeIf { it.isNotBlank() }
                        ?: "OpenRouter returned an empty response."
                }
            }
            is OpenRouterClientResult.Error -> result.message
        }
    }

    private fun executeToolCalls(toolCalls: List<OpenRouterToolCall>): List<OpenRouterMessage> {
        val validToolNames = MedTrackMcpCatalog.tools.map { it.name }.toSet() +
            MedTrackMcpCatalog.CREATE_VISIT_ALIAS
        return toolCalls.map { toolCall ->
            if (toolCall.name !in validToolNames) {
                return@map OpenRouterMessage.toolResult(
                    toolCallId = toolCall.id,
                    content = "OpenRouter requested an unknown tool: ${toolCall.name}"
                )
            }

            val arguments = try {
                toolCall.argumentsObject()
            } catch (_: JSONException) {
                return@map OpenRouterMessage.toolResult(
                    toolCallId = toolCall.id,
                    content = "OpenRouter provided invalid arguments for ${toolCall.name}."
                )
            }

            val response = mcpClient.callTool(name = toolCall.name, arguments = arguments)
            OpenRouterMessage.toolResult(
                toolCallId = toolCall.id,
                content = response.toToolResultText()
            )
        }
    }

    private fun JSONObject.toToolResultText(): String {
        optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let {
            return it
        }

        val result = optJSONObject("result") ?: return "The tool call failed."
        return result.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: result.optJSONObject("structuredContent")?.optString("message")
            ?: "The tool completed."
    }

    private fun String.shouldUseMedTrackTools(): Boolean {
        val text = lowercase()
        return listOf(
            "patient",
            "visit",
            "room",
            "bed",
            "medicine",
            "tablet",
            "dosage",
            "report",
            "blood",
            "task",
            "follow",
            "appointment",
            "discharge",
            "admit",
            "create",
            "add",
            "search",
            "move",
            "update",
            "schedule",
            "reschedule"
        ).any { keyword -> keyword in text }
    }

    private fun String.shouldRequireMedTrackToolCall(): Boolean {
        val text = lowercase()
        val changeKeywords = listOf(
            "create",
            "add",
            "register",
            "admit",
            "move",
            "update",
            "change",
            "schedule",
            "reschedule",
            "remove",
            "delete",
            "discharge",
            "mark"
        )
        val medTrackKeywords = listOf(
            "patient",
            "visit",
            "room",
            "bed",
            "medicine",
            "tablet",
            "task",
            "follow",
            "appointment",
            "report"
        )
        return changeKeywords.any { keyword -> keyword in text } &&
            medTrackKeywords.any { keyword -> keyword in text }
    }
}
