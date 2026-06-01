package com.medtrack.app.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeLlmBridge @Inject constructor() {
    external fun nativeSmokeTest(): String
    external fun nativeLlamaVersion(): String
    external fun nativeLoadModel(modelPath: String): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeReleaseModel(handle: Long)

    companion object {
        init {
            System.loadLibrary("medtrack_native")
        }
    }
}
