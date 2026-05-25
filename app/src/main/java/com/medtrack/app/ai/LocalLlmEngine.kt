package com.medtrack.app.ai

interface LocalLlmEngine {
    suspend fun generate(prompt: String): String
}
