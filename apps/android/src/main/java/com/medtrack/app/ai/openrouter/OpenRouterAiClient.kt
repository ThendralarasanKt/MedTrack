package com.medtrack.app.ai.openrouter

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class OpenRouterAiClient @Inject constructor(
    private val apiKeyProvider: OpenRouterApiKeyProvider
) {
    fun createChatCompletion(request: OpenRouterChatRequest): OpenRouterClientResult {
        val models = OPENROUTER_FALLBACK_MODELS
            .let { fallbackModels ->
                if (request.model in fallbackModels) fallbackModels else listOf(request.model) + fallbackModels
            }
            .distinct()

        var lastError: OpenRouterClientResult.Error? = null
        models.forEach { model ->
            when (val result = createChatCompletionOnce(request.copy(model = model))) {
                is OpenRouterClientResult.Success -> return result
                is OpenRouterClientResult.Error -> lastError = result
            }
        }

        return lastError?.let { error ->
            OpenRouterClientResult.Error(
                message = "OpenRouter failed after trying ${models.size} model(s). Last error: ${error.message}",
                httpStatusCode = error.httpStatusCode
            )
        } ?: OpenRouterClientResult.Error("OpenRouter request failed.")
    }

    private fun createChatCompletionOnce(request: OpenRouterChatRequest): OpenRouterClientResult {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) {
            return OpenRouterClientResult.Error(
                message = "OpenRouter API key is not configured. Add openrouter.api.key to local.properties for this developer-only phase."
            )
        }

        return runCatching {
            val connection = (URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val body = request.toJson().toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { output ->
                output.write(body)
            }

            val responseBody = connection.readResponseBody()
            val responseJson = responseBody.takeIf { it.isNotBlank() }?.let(::JSONObject)

            if (connection.responseCode in 200..299 && responseJson != null) {
                val response = OpenRouterChatResponse.fromJson(responseJson)
                response.error?.let { error ->
                    OpenRouterClientResult.Error(error.message, connection.responseCode)
                } ?: OpenRouterClientResult.Success(response)
            } else {
                val errorMessage = responseJson
                    ?.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "OpenRouter request failed with HTTP ${connection.responseCode}."
                OpenRouterClientResult.Error(errorMessage, connection.responseCode)
            }
        }.getOrElse { error ->
            when (error) {
                is UnknownHostException -> OpenRouterClientResult.Error(
                    "Unable to reach OpenRouter. Check that this device or emulator has internet access and DNS working."
                )
                is SocketTimeoutException -> OpenRouterClientResult.Error("OpenRouter request timed out.")
                else -> OpenRouterClientResult.Error(error.message ?: "OpenRouter request failed.")
            }
        }
    }

    private fun HttpURLConnection.readResponseBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }.orEmpty()
    }

    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}

sealed class OpenRouterClientResult {
    data class Success(val response: OpenRouterChatResponse) : OpenRouterClientResult()
    data class Error(val message: String, val httpStatusCode: Int? = null) : OpenRouterClientResult()
}
