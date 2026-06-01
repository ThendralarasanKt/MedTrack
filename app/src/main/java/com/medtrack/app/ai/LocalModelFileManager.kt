package com.medtrack.app.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun defaultModelFile(): File {
        val modelDirectory = File(context.filesDir, MODEL_DIRECTORY_NAME)
        if (!modelDirectory.exists()) {
            modelDirectory.mkdirs()
        }
        return File(modelDirectory, DEFAULT_MODEL_FILE_NAME)
    }

    fun defaultModelStatus(): LocalModelStatus {
        val modelFile = defaultModelFile()
        return LocalModelStatus(
            path = modelFile.absolutePath,
            exists = modelFile.exists(),
            sizeBytes = if (modelFile.exists()) modelFile.length() else 0L
        )
    }

    companion object {
        private const val MODEL_DIRECTORY_NAME = "models"
        private const val DEFAULT_MODEL_FILE_NAME = "medtrack-assistant.gguf"
    }
}

data class LocalModelStatus(
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long
)
