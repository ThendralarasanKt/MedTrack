package com.medtrack.app.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts attachment bytes at rest. Stored files use an `.enc` suffix and are not
 * readable as plaintext PDFs/images when browsed outside the unlocked app.
 */
@Singleton
class SecureFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun encryptToFile(plaintext: ByteArray, destination: File) {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        EncryptedFile.Builder(
            context,
            destination,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileOutput().use { output ->
            output.write(plaintext)
        }
    }

    fun decryptToBytes(encryptedFile: File): ByteArray {
        EncryptedFile.Builder(
            context,
            encryptedFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileInput().use { input ->
            return input.readBytes()
        }
    }

    fun decryptToCacheFile(encryptedFile: File, displayName: String): File {
        val cacheDir = File(context.cacheDir, "decrypted-reports").apply { mkdirs() }
        val target = File(cacheDir, displayName)
        if (target.exists()) target.delete()
        FileOutputStream(target).use { output ->
            output.write(decryptToBytes(encryptedFile))
        }
        return target
    }

    fun copyUriToEncryptedFile(source: File, destination: File) {
        encryptToFile(source.readBytes(), destination)
    }

    fun isEncryptedPath(path: String): Boolean =
        path.endsWith(ENCRYPTED_SUFFIX, ignoreCase = true)

    companion object {
        const val ENCRYPTED_SUFFIX = ".enc"
    }
}
