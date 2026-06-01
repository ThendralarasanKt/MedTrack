package com.medtrack.app.ai

import android.util.Log
import com.medtrack.app.mcp.client.InAppMcpClient
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject

@Singleton
class AiAssistantOrchestrator @Inject constructor(
    private val llmEngine: LocalLlmEngine,
    private val mcpClient: InAppMcpClient,
    private val nativeLlmBridge: NativeLlmBridge,
    private val llamaCppEngine: LlamaCppEngine
) {
    suspend fun handleUserMessage(message: String): String {
        if (message.trim().equals("native smoke test", ignoreCase = true)) {
            val result = nativeLlmBridge.nativeSmokeTest()
            Log.i(TAG, "Native smoke test result: $result")
            return result
        }
        if (message.trim().equals("llama version test", ignoreCase = true)) {
            val result = nativeLlmBridge.nativeLlamaVersion()
            Log.i(TAG, "llama.cpp version test result: $result")
            return result
        }
        if (message.trim().equals("llama model path", ignoreCase = true)) {
            val result = llamaCppEngine.modelSetupMessage()
            Log.i(TAG, "llama.cpp model path result: $result")
            return result
        }
        if (message.trim().equals("llama load model test", ignoreCase = true)) {
            val result = llamaCppEngine.loadDefaultModelForTest()
            Log.i(TAG, "llama.cpp model load result: $result")
            return result
        }
        if (message.trim().equals("llama generate test", ignoreCase = true)) {
            val result = llamaCppEngine.generateRawForTest("Say hello in one short sentence.")
            Log.i(TAG, "llama.cpp generate test result: $result")
            return result
        }
        if (message.trim().startsWith("llama generate ", ignoreCase = true)) {
            val prompt = message.trim().replaceFirst(Regex("(?i)^llama\\s+generate\\s+"), "").trim()
            if (prompt.isBlank()) {
                return "Type a prompt after: llama generate"
            }
            val result = llamaCppEngine.generateRawForTest(prompt)
            Log.i(TAG, "llama.cpp generate custom result: $result")
            return result
        }
        if (message.trim().equals("llama tool decision test", ignoreCase = true)) {
            val result = llamaCppEngine.generateToolDecisionForTest("Move Ramesh from 420A to 530B")
            Log.i(TAG, "llama.cpp tool decision test result: $result")
            return result
        }
        if (message.trim().startsWith("llama decide ", ignoreCase = true)) {
            val request = message.trim().replaceFirst(Regex("(?i)^llama\\s+decide\\s+"), "").trim()
            if (request.isBlank()) {
                return "Type a request after: llama decide"
            }
            val result = llamaCppEngine.generateToolDecisionForTest(request)
            Log.i(TAG, "llama.cpp tool decision custom result: $result")
            return result
        }

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

    companion object {
        private const val TAG = "AiAssistantOrchestrator"
    }
}
