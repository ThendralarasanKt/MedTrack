package com.medtrack.app.ai.openrouter

import com.medtrack.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterApiKeyProvider @Inject constructor() {
    fun getApiKey(): String = BuildConfig.OPENROUTER_API_KEY.trim()
}
