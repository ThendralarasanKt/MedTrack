package com.medtrack.app.hybrid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.hybrid.profile.ProfileCache
import com.medtrack.app.hybrid.profile.ProfileOutboxItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileCacheTest {
    private lateinit var cache: ProfileCache

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cache = ProfileCache(context).apply { inMemoryOnly = true }
    }

    @Test
    fun snapshotIsAccountBound() {
        cache.saveConfirmed(
            accountId = "acct-a",
            userId = "user-a",
            email = "a@example.com",
            profileJson = JSONObject().put("displayName", "A").put("version", 1),
            accessJson = JSONObject().put("entitlement", JSONObject().put("accessState", "AUTHORIZED").put("label", "Pilot access"))
        )
        assertEquals("A", cache.snapshot("user-a")?.profileJson?.optString("displayName"))
        assertNull(cache.snapshot("user-b"))
        assertEquals("user-a", cache.latest()?.userId)
    }

    @Test
    fun outboxStaysOnOriginalAccount() {
        cache.enqueue(
            ProfileOutboxItem(
                operationId = "op-1",
                accountId = "acct-a",
                userId = "user-a",
                baseVersion = 1,
                patchJson = JSONObject().put("displayName", "Queued"),
                queuedAt = 1L,
                state = "QUEUED",
                lastErrorCode = null
            )
        )
        assertTrue(cache.outboxFor("user-a").any { it.operationId == "op-1" })
        assertTrue(cache.outboxFor("user-b").isEmpty())
        cache.updateOutbox("op-1", "CONFLICT", "CONFLICT")
        assertEquals("CONFLICT", cache.outboxFor("user-a").single().state)
    }
}
