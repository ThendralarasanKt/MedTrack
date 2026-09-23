package com.medtrack.app.hybrid.profile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class CachedProfile(
    val accountId: String,
    val userId: String,
    val email: String?,
    val profileJson: JSONObject,
    val accessJson: JSONObject?,
    val fetchedAt: Long,
    val stale: Boolean,
    val linksJson: JSONObject
)

data class ProfileOutboxItem(
    val operationId: String,
    val accountId: String,
    val userId: String,
    val baseVersion: Int,
    val patchJson: JSONObject,
    val queuedAt: Long,
    val state: String,
    val lastErrorCode: String?
)

@Singleton
class ProfileCache @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    internal var inMemoryOnly: Boolean = false
    private var memorySnapshot: String? = null
    private var memoryOutbox: String? = null

    fun latest(): CachedProfile? {
        val json = loadSnapshot() ?: return null
        return snapshot(json.optString("userId"))
    }

    fun snapshot(userId: String): CachedProfile? {
        val json = loadSnapshot() ?: return null
        if (json.optString("userId") != userId) return null
        return CachedProfile(
            accountId = json.optString("accountId"),
            userId = json.optString("userId"),
            email = json.optString("email").ifBlank { null },
            profileJson = json.optJSONObject("profile") ?: JSONObject(),
            accessJson = json.optJSONObject("access"),
            fetchedAt = json.optLong("fetchedAt"),
            stale = json.optBoolean("stale"),
            linksJson = json.optJSONObject("links") ?: JSONObject()
        )
    }

    fun saveConfirmed(
        accountId: String,
        userId: String,
        email: String?,
        profileJson: JSONObject,
        accessJson: JSONObject?,
        fetchedAt: Long = System.currentTimeMillis()
    ) {
        val previous = loadSnapshot()
        val links = if (previous?.optString("userId") == userId) {
            previous.optJSONObject("links") ?: JSONObject()
        } else {
            JSONObject()
        }
        persistSnapshot(
            JSONObject()
                .put("accountId", accountId)
                .put("userId", userId)
                .put("email", email ?: JSONObject.NULL)
                .put("profile", profileJson)
                .put("access", accessJson ?: JSONObject.NULL)
                .put("fetchedAt", fetchedAt)
                .put("stale", false)
                .put("links", links)
        )
    }

    fun markStale(userId: String) {
        val json = loadSnapshot() ?: return
        if (json.optString("userId") != userId) return
        persistSnapshot(json.put("stale", true))
    }

    fun putLink(userId: String, key: String, localId: String, sourceVersion: Int) {
        val json = loadSnapshot() ?: return
        if (json.optString("userId") != userId) return
        val links = json.optJSONObject("links") ?: JSONObject()
        links.put(
            key,
            JSONObject()
                .put("localId", localId)
                .put("sourceVersion", sourceVersion)
                .put("linkedAt", System.currentTimeMillis())
        )
        persistSnapshot(json.put("links", links))
    }

    fun outboxFor(userId: String): List<ProfileOutboxItem> {
        val array = loadOutbox()
        return buildList {
            for (i in 0 until array.length()) {
                val item = parseOutbox(array.getJSONObject(i))
                if (item.userId == userId) add(item)
            }
        }
    }

    fun enqueue(item: ProfileOutboxItem) {
        val array = loadOutbox()
        for (i in 0 until array.length()) {
            if (array.getJSONObject(i).optString("operationId") == item.operationId) {
                array.put(i, toJson(item))
                persistOutbox(array)
                return
            }
        }
        array.put(toJson(item))
        persistOutbox(array)
    }

    fun updateOutbox(operationId: String, state: String, lastErrorCode: String? = null) {
        val array = loadOutbox()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (row.optString("operationId") == operationId) {
                row.put("state", state)
                if (lastErrorCode == null) row.put("lastErrorCode", JSONObject.NULL)
                else row.put("lastErrorCode", lastErrorCode)
                persistOutbox(array)
                return
            }
        }
    }

    fun removeOutbox(operationId: String) {
        val array = loadOutbox()
        val next = JSONArray()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (row.optString("operationId") != operationId) next.put(row)
        }
        persistOutbox(next)
    }

    private fun loadSnapshot(): JSONObject? {
        val raw = if (inMemoryOnly) memorySnapshot else prefs().getString(KEY_SNAPSHOT, null)
        return raw?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun persistSnapshot(json: JSONObject) {
        val raw = json.toString()
        if (inMemoryOnly) {
            memorySnapshot = raw
            return
        }
        prefs().edit().putString(KEY_SNAPSHOT, raw).apply()
    }

    private fun loadOutbox(): JSONArray {
        val raw = if (inMemoryOnly) memoryOutbox else prefs().getString(KEY_OUTBOX, null)
        return if (raw.isNullOrBlank()) JSONArray() else runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun persistOutbox(array: JSONArray) {
        val raw = array.toString()
        if (inMemoryOnly) {
            memoryOutbox = raw
            return
        }
        prefs().edit().putString(KEY_OUTBOX, raw).apply()
    }

    private fun parseOutbox(json: JSONObject) = ProfileOutboxItem(
        operationId = json.getString("operationId"),
        accountId = json.getString("accountId"),
        userId = json.getString("userId"),
        baseVersion = json.getInt("baseVersion"),
        patchJson = json.getJSONObject("patch"),
        queuedAt = json.getLong("queuedAt"),
        state = json.getString("state"),
        lastErrorCode = json.optString("lastErrorCode").ifBlank { null }
    )

    private fun toJson(item: ProfileOutboxItem) = JSONObject()
        .put("operationId", item.operationId)
        .put("accountId", item.accountId)
        .put("userId", item.userId)
        .put("baseVersion", item.baseVersion)
        .put("patch", item.patchJson)
        .put("queuedAt", item.queuedAt)
        .put("state", item.state)
        .put("lastErrorCode", item.lastErrorCode ?: JSONObject.NULL)

    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS = "medtrack_profile_cache"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_OUTBOX = "outbox"
    }
}
