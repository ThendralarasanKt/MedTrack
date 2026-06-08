package com.medtrack.app.ai.openrouter

import org.json.JSONArray
import org.json.JSONObject

const val OPENROUTER_FREE_MODEL = "openrouter/free"
const val OPENROUTER_PREFERRED_MODEL = "openai/gpt-oss-120b:free"

val OPENROUTER_FALLBACK_MODELS: List<String> = listOf(
    OPENROUTER_PREFERRED_MODEL,
    OPENROUTER_FREE_MODEL,
    "nvidia/nemotron-3-ultra-550b-a55b:free",
    "qwen/qwen3-coder:free",
    "z-ai/glm-4.5-air:free",
    "meta-llama/llama-3.3-70b-instruct:free"
)

data class OpenRouterChatRequest(
    val messages: List<OpenRouterMessage>,
    val tools: List<OpenRouterTool> = emptyList(),
    val model: String = OPENROUTER_PREFERRED_MODEL,
    val toolChoice: String? = "auto",
    val provider: OpenRouterProviderOptions = OpenRouterProviderOptions()
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("model", model)
            .put("messages", JSONArray(messages.map { it.toJson() }))
            .also { json ->
                if (tools.isNotEmpty()) {
                    json.put("tools", JSONArray(tools.map { it.toJson() }))
                    toolChoice?.let { json.put("tool_choice", it) }
                }
                provider.toJson().takeIf { it.length() > 0 }?.let {
                    json.put("provider", it)
                }
            }
}

data class OpenRouterProviderOptions(
    val requireParameters: Boolean = false,
    val zeroDataRetention: Boolean? = null,
    val dataCollection: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .also { json ->
                if (requireParameters) {
                    json.put("require_parameters", true)
                }
                zeroDataRetention?.let { json.put("zdr", it) }
                dataCollection?.let { json.put("data_collection", it) }
            }
}

data class OpenRouterMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<OpenRouterToolCall> = emptyList(),
    val toolCallId: String? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("role", role)
            .also { json ->
                content?.let { json.put("content", it) }
                toolCallId?.let { json.put("tool_call_id", it) }
                if (toolCalls.isNotEmpty()) {
                    json.put("tool_calls", JSONArray(toolCalls.map { it.toJson() }))
                }
            }

    companion object {
        fun system(content: String): OpenRouterMessage =
            OpenRouterMessage(role = "system", content = content)

        fun user(content: String): OpenRouterMessage =
            OpenRouterMessage(role = "user", content = content)

        fun assistant(content: String): OpenRouterMessage =
            OpenRouterMessage(role = "assistant", content = content)

        fun toolResult(toolCallId: String, content: String): OpenRouterMessage =
            OpenRouterMessage(role = "tool", content = content, toolCallId = toolCallId)
    }
}

data class OpenRouterTool(
    val name: String,
    val description: String,
    val parameters: JSONObject
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", parameters)
            )
}

data class OpenRouterToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("arguments", argumentsJson)
            )

    fun argumentsObject(): JSONObject = JSONObject(argumentsJson.ifBlank { "{}" })

    companion object {
        fun fromJson(json: JSONObject): OpenRouterToolCall? {
            val function = json.optJSONObject("function") ?: return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = function.optString("name").takeIf { it.isNotBlank() } ?: return null
            return OpenRouterToolCall(
                id = id,
                name = name,
                argumentsJson = function.optString("arguments", "{}")
            )
        }
    }
}

data class OpenRouterChatResponse(
    val id: String,
    val model: String,
    val message: OpenRouterAssistantMessage?,
    val error: OpenRouterError? = null
) {
    val isSuccess: Boolean = error == null && message != null

    companion object {
        fun fromJson(json: JSONObject): OpenRouterChatResponse {
            val error = json.optJSONObject("error")?.let(OpenRouterError::fromJson)
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
            val message = choice?.optJSONObject("message")?.let(OpenRouterAssistantMessage::fromJson)
            return OpenRouterChatResponse(
                id = json.optString("id"),
                model = json.optString("model"),
                message = message,
                error = error
            )
        }
    }
}

data class OpenRouterAssistantMessage(
    val content: String,
    val toolCalls: List<OpenRouterToolCall>
) {
    val hasToolCalls: Boolean = toolCalls.isNotEmpty()

    companion object {
        fun fromJson(json: JSONObject): OpenRouterAssistantMessage {
            val toolCallsJson = json.optJSONArray("tool_calls") ?: JSONArray()
            val toolCalls = (0 until toolCallsJson.length())
                .mapNotNull { index -> toolCallsJson.optJSONObject(index) }
                .mapNotNull(OpenRouterToolCall::fromJson)

            return OpenRouterAssistantMessage(
                content = json.optString("content"),
                toolCalls = toolCalls
            )
        }
    }
}

data class OpenRouterError(
    val code: Int?,
    val message: String,
    val type: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): OpenRouterError =
            OpenRouterError(
                code = json.optIntOrNull("code"),
                message = json.optString("message", "OpenRouter request failed."),
                type = json.optString("type").takeIf { it.isNotBlank() }
            )
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
