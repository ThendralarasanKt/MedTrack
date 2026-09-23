package com.medtrack.app.mcp.transport

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpBearerTokenProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun token(): String {
        val prefs = encryptedPrefs()
        val existing = prefs.getString(KEY_MCP_BEARER, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MCP_BEARER, generated).apply()
        return generated
    }

    private fun encryptedPrefs() =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    companion object {
        const val PREFS_NAME = "medtrack_secure_prefs"
        private const val KEY_MCP_BEARER = "mcp_bearer_v1"
    }
}
