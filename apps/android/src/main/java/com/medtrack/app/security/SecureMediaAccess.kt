package com.medtrack.app.security

import android.content.Context
import com.medtrack.app.data.storage.FileStorageManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecureStorageEntryPoint {
    fun fileStorageManager(): FileStorageManager
    fun secureFileStore(): SecureFileStore
}

object SecureMediaAccess {
    fun fileStorage(context: Context): FileStorageManager =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SecureStorageEntryPoint::class.java
        ).fileStorageManager()

    fun secureFileStore(context: Context): SecureFileStore =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SecureStorageEntryPoint::class.java
        ).secureFileStore()

    /** Coil-compatible model: ByteArray for encrypted files, File otherwise. */
    fun imageModel(context: Context, path: String): Any? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return if (SecureFileStore.ENCRYPTED_SUFFIX.let { path.endsWith(it, ignoreCase = true) }) {
            runCatching { secureFileStore(context).decryptToBytes(file) }.getOrNull()
        } else {
            file
        }
    }
}
