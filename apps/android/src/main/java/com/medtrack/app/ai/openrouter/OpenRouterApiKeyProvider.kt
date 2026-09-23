package com.medtrack.app.ai.openrouter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider credentials are backend-only (HY-09). The APK must not embed an OpenRouter key.
 */
@Singleton
class OpenRouterApiKeyProvider @Inject constructor() {
    fun getApiKey(): String = ""
}
