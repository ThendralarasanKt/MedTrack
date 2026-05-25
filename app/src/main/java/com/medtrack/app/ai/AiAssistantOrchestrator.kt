package com.medtrack.app.ai

import com.medtrack.app.mcp.client.InAppMcpClient
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject

@Singleton
class AiAssistantOrchestrator @Inject constructor(
    private val llmEngine: LocalLlmEngine,
    private val mcpClient: InAppMcpClient
) {
    suspend fun handleUserMessage(message: String): String {
        val tools = mcpClient.listTools()
        val prompt = buildPrompt(userMessage = message, tools = tools)
        val modelOutput = llmEngine.generate(prompt)

        val decision = try {
            JSONObject(modelOutput)
        } catch (_: JSONException) {
            return "I could not understand the model response."
        }

        return when (decision.optString("type")) {
            "tool_call" -> executeToolCall(decision)
            "answer" -> decision.optString("message", "I could not understand that request.")
            else -> "I could not understand that request."
        }
    }

    private fun buildPrompt(userMessage: String, tools: JSONObject): String =
        """
        You are MedTrack assistant.

        Available MCP tools:
        $tools

        If the user asks to move, change, or update a patient's room, return only JSON:
        {
          "type": "tool_call",
          "tool": "update_patient_room",
          "arguments": {
            "patientName": "...",
            "currentRoomNumber": "...",
            "newRoomNumber": "..."
          }
        }

        If no tool is needed, return only JSON:
        {
          "type": "answer",
          "message": "..."
        }

        User request: $userMessage
        """.trimIndent()

    private fun executeToolCall(decision: JSONObject): String {
        val toolName = decision.optString("tool")
        val arguments = decision.optJSONObject("arguments") ?: JSONObject()

        if (toolName.isBlank()) {
            return "The model did not choose a valid tool."
        }

        val response = mcpClient.callTool(name = toolName, arguments = arguments)
        val result = response.optJSONObject("result")
            ?: return response.optJSONObject("error")?.optString("message")
                ?: "The tool call failed."

        return result.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: result.optJSONObject("structuredContent")?.optString("message")
            ?: "The tool completed."
    }
}
