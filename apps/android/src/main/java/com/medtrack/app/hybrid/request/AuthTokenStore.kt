package com.medtrack.app.hybrid.request

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    internal var inMemoryOnly: Boolean = false
    private var memory: String? = null

    fun token(): String? = if (inMemoryOnly) memory else prefs().getString(KEY, null)

    fun save(token: String) {
        if (inMemoryOnly) {
            memory = token
            return
        }
        prefs().edit().putString(KEY, token).apply()
    }

    fun clear() {
        if (inMemoryOnly) {
            memory = null
            return
        }
        prefs().edit().remove(KEY).apply()
    }

    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        "medtrack_gateway_token",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY = "id_token"
    }
}
