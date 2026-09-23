package com.medtrack.app.hybrid.account

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class AccountBindingStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    internal var inMemoryOnly: Boolean = false
    private var memory: BoundAccount? = null
    private var memoryDeviceId: String? = null
    private var memorySessionOpen: Boolean = false

    fun active(): BoundAccount? {
        if (inMemoryOnly) return memory
        val raw = prefs().getString(KEY_BINDING, null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    fun bind(account: BoundAccount) {
        val current = active()
        if (current != null && current.ownerAccountId != account.ownerAccountId) {
            throw AccountMismatchException(
                "This device already holds records for a different account. " +
                    "Sign-in alone does not open another user's database."
            )
        }
        if (current != null &&
            current.authSubject.isNotBlank() &&
            account.authSubject.isNotBlank() &&
            current.authSubject != account.authSubject
        ) {
            throw AccountMismatchException(
                "This device already holds records for a different identity. " +
                    "Sign-in alone does not open another user's database."
            )
        }
        if (current != null && current.kind != account.kind) {
            throw AccountMismatchException(
                "Synthetic debug records and Google accounts stay on separate workspaces."
            )
        }
        persist(account)
    }

    fun markOffline() {
        val current = active() ?: return
        persist(current.copy(onlineAuthorized = false))
    }

    fun clearOnlineSession() {
        val current = active() ?: return
        persist(current.copy(onlineAuthorized = false))
    }

    /** Open until an explicit sign-out. Separate from who owns the local records. */
    fun isSessionOpen(): Boolean {
        if (inMemoryOnly) return memorySessionOpen
        return prefs().getBoolean(KEY_SESSION_OPEN, false)
    }

    fun setSessionOpen(open: Boolean) {
        if (inMemoryOnly) {
            memorySessionOpen = open
            return
        }
        prefs().edit().putBoolean(KEY_SESSION_OPEN, open).apply()
    }

    fun deviceId(): String {
        if (inMemoryOnly) {
            val existing = memoryDeviceId
            if (existing != null) return existing
            val created = UUID.randomUUID().toString()
            memoryDeviceId = created
            return created
        }
        val prefs = prefs()
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun persist(account: BoundAccount) {
        if (inMemoryOnly) {
            memory = account
            return
        }
        prefs().edit().putString(KEY_BINDING, toJson(account).toString()).apply()
    }

    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun parse(raw: String): BoundAccount {
        val json = JSONObject(raw)
        val issuer = json.getString("authIssuer")
        val kind = runCatching { AccountKind.valueOf(json.optString("kind")) }.getOrNull()
            ?: if (issuer.startsWith("https://securetoken.google.com/")) AccountKind.GOOGLE else AccountKind.SYNTHETIC
        return BoundAccount(
            userId = json.getString("userId"),
            accountId = json.getString("accountId"),
            ownerAccountId = json.getString("ownerAccountId"),
            actorPersonId = json.getString("actorPersonId"),
            authIssuer = issuer,
            authSubject = json.getString("authSubject"),
            deviceId = json.getString("deviceId"),
            displayName = json.optString("displayName"),
            onlineAuthorized = json.optBoolean("onlineAuthorized", false),
            kind = kind
        )
    }

    private fun toJson(account: BoundAccount) = JSONObject()
        .put("userId", account.userId)
        .put("accountId", account.accountId)
        .put("ownerAccountId", account.ownerAccountId)
        .put("actorPersonId", account.actorPersonId)
        .put("authIssuer", account.authIssuer)
        .put("authSubject", account.authSubject)
        .put("deviceId", account.deviceId)
        .put("displayName", account.displayName)
        .put("onlineAuthorized", account.onlineAuthorized)
        .put("kind", account.kind.name)

    companion object {
        private const val PREFS = "medtrack_account_binding"
        private const val KEY_BINDING = "binding"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_OPEN = "session_open"
    }
}

class AccountMismatchException(message: String) : IllegalStateException(message)
class UnprovisionedIdentityException(
    message: String = "This Google account is not provisioned for MedTrack."
) : IllegalStateException(message)

class AuthorizationDeniedException(message: String) : IllegalStateException(message)

class GatewayUnavailableException(message: String) : IllegalStateException(message)
