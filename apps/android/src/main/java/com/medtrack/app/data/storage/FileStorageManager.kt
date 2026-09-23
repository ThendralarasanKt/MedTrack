package com.medtrack.app.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.medtrack.app.security.SecureFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileStorageManager: Manages encrypted clinical files on the device.
 *
 * Files are written under reports/ or patients/ using SecureFileStore so browsing
 * app storage outside the unlocked app does not reveal readable content.
 */
@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureFileStore: SecureFileStore
) {
    fun saveImage(uri: Uri, visitId: Int): String? {
        return try {
            val directory = File(context.filesDir, "reports/visit_$visitId")
            if (!directory.exists()) directory.mkdirs()

            val fileName = "IMG_${System.currentTimeMillis()}.jpg${SecureFileStore.ENCRYPTED_SUFFIX}"
            val destFile = File(directory, fileName)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            secureFileStore.encryptToFile(bytes, destFile)
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun savePatientPhoto(uri: Uri, patientId: Int): String? {
        return try {
            val directory = File(context.filesDir, "patients")
            if (!directory.exists()) directory.mkdirs()

            val destFile = File(
                directory,
                "patient_${patientId}_${System.currentTimeMillis()}.jpg${SecureFileStore.ENCRYPTED_SUFFIX}"
            )
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            secureFileStore.encryptToFile(bytes, destFile)
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveReport(uri: Uri, visitId: Int, displayName: String? = null): SavedReportFile? {
        return try {
            val directory = File(context.filesDir, "reports/visit_$visitId")
            if (!directory.exists()) directory.mkdirs()

            val sourceName = queryDisplayName(uri) ?: "report_${System.currentTimeMillis()}"
            val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "")
            val chosenName = displayName?.trim().orEmpty()
            val displayFileName = when {
                chosenName.isBlank() -> sourceName
                extension.isBlank() || chosenName.endsWith(".$extension", ignoreCase = true) -> chosenName
                else -> "$chosenName.$extension"
            }
            val safeBaseName = displayFileName
                .substringBeforeLast('.')
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .ifBlank { "report" }
            val plainFileName = if (extension.isBlank()) {
                "${safeBaseName}_${System.currentTimeMillis()}"
            } else {
                "${safeBaseName}_${System.currentTimeMillis()}.$extension"
            }
            val destFile = File(directory, plainFileName + SecureFileStore.ENCRYPTED_SUFFIX)

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            secureFileStore.encryptToFile(bytes, destFile)

            val mimeType = context.contentResolver.getType(uri).orEmpty()
            SavedReportFile(
                fileName = displayFileName,
                filePath = destFile.absolutePath,
                fileType = when {
                    mimeType == "application/pdf" || plainFileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
                    else -> "IMAGE"
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
        }
    }

    fun getFile(path: String): File = File(path)

    /**
     * Returns a plaintext cache file suitable for FileProvider / external viewers.
     */
    fun getShareableFile(path: String, displayName: String): File {
        val encrypted = File(path)
        return if (secureFileStore.isEncryptedPath(path) && encrypted.exists()) {
            secureFileStore.decryptToCacheFile(encrypted, displayName)
        } else {
            encrypted
        }
    }

    fun deleteFile(path: String): Boolean = File(path).delete()
}

data class SavedReportFile(
    val fileName: String,
    val filePath: String,
    val fileType: String
)
