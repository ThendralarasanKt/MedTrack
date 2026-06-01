package com.medtrack.app.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppEngine @Inject constructor(
    private val nativeLlmBridge: NativeLlmBridge,
    private val modelFileManager: LocalModelFileManager
) : LocalLlmEngine {
    private var modelHandle: Long = 0L

    fun modelSetupMessage(): String {
        val status = modelFileManager.defaultModelStatus()
        return if (status.exists) {
            "Local model found at ${status.path} (${status.sizeBytes} bytes). Run: llama load model test"
        } else {
            "No local model found. Put a GGUF model file here: ${status.path}"
        }
    }

    fun loadDefaultModelForTest(): String {
        val status = modelFileManager.defaultModelStatus()
        if (!status.exists) {
            return "No local model found. Put a GGUF model file here: ${status.path}"
        }

        return if (loadModel(status.path)) {
            "Loaded local model from ${status.path} (${status.sizeBytes} bytes). Token generation is the next integration step."
        } else {
            "llama.cpp could not load the model at ${status.path}. Check that it is a valid GGUF file for this phone."
        }
    }

    fun loadModel(modelPath: String): Boolean {
        releaseModel()
        modelHandle = nativeLlmBridge.nativeLoadModel(modelPath)
        return modelHandle != 0L
    }

    fun releaseModel() {
        if (modelHandle != 0L) {
            nativeLlmBridge.nativeReleaseModel(modelHandle)
            modelHandle = 0L
        }
    }

    fun generateRawForTest(prompt: String): String {
        val formattedPrompt = """
            <|im_start|>system
            You are a concise offline assistant inside the MedTrack app.
            <|im_end|>
            <|im_start|>user
            $prompt
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        return generateWithLoadedModel(
            formattedPrompt = formattedPrompt,
            maxTokens = 16
        )
    }

    fun generateToolDecisionForTest(userRequest: String): String {
        val formattedPrompt = """
            <|im_start|>system
            Return only minified JSON. No explanation.
            If the user asks to move a patient from one room to another, return:
            {"type":"tool_call","tool":"update_patient_room","arguments":{"patientName":"NAME","currentRoomNumber":"OLD","newRoomNumber":"NEW"}}
            <|im_end|>
            <|im_start|>user
            $userRequest
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        return generateWithLoadedModel(
            formattedPrompt = formattedPrompt,
            maxTokens = 96
        )
    }

    private fun generateWithLoadedModel(formattedPrompt: String, maxTokens: Int): String {
        val status = modelFileManager.defaultModelStatus()
        if (!status.exists) {
            return "No local model found. Put a GGUF model file here: ${status.path}"
        }
        if (modelHandle == 0L && !loadModel(status.path)) {
            return "llama.cpp could not load the model at ${status.path}. Check that it is a valid GGUF file for this phone."
        }

        return nativeLlmBridge.nativeGenerate(
            handle = modelHandle,
            prompt = formattedPrompt,
            maxTokens = maxTokens
        ).ifBlank {
            "The local model returned an empty response."
        }
    }

    override suspend fun generate(prompt: String): String {
        return if (modelHandle == 0L) {
            """{"type":"answer","message":"Local model is not loaded yet."}"""
        } else {
            """{"type":"answer","message":"Local model loaded. Token generation is the next integration step."}"""
        }
    }
}
