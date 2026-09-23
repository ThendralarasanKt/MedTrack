package com.medtrack.app.hybrid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.hybrid.account.AccountBindingStore
import com.medtrack.app.hybrid.account.AccountKind
import com.medtrack.app.hybrid.account.AccountMismatchException
import com.medtrack.app.hybrid.account.AuthCoordinator
import com.medtrack.app.hybrid.account.AuthSessionState
import com.medtrack.app.hybrid.account.AuthorizedCallback
import com.medtrack.app.hybrid.account.UnprovisionedIdentityException
import com.medtrack.app.hybrid.gateway.GatewayBootstrap
import com.medtrack.app.hybrid.gateway.GatewayCapabilities
import com.medtrack.app.hybrid.gateway.GatewayException
import com.medtrack.app.hybrid.gateway.GatewayJob
import com.medtrack.app.hybrid.gateway.InferenceGateway
import com.medtrack.app.hybrid.profile.ProfileCache
import com.medtrack.app.hybrid.profile.ProfileSyncCallback
import com.medtrack.app.hybrid.request.AuthTokenProvider
import com.medtrack.app.hybrid.request.AuthTokenStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthCoordinatorTest {
    private lateinit var bindings: AccountBindingStore
    private lateinit var tokens: AuthTokenStore
    private lateinit var provider: RecordingTokenProvider
    private lateinit var gateway: FakeGateway
    private lateinit var profiles: ProfileCache
    private lateinit var coordinator: AuthCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        bindings = AccountBindingStore(context).apply { inMemoryOnly = true }
        tokens = AuthTokenStore(context).apply { inMemoryOnly = true }
        profiles = ProfileCache(context).apply { inMemoryOnly = true }
        provider = RecordingTokenProvider(tokens)
        gateway = FakeGateway()
        coordinator = buildCoordinator()
    }

    @Test
    fun openSessionSurvivesANewProcessUntilSignOut() = runBlocking {
        coordinator.continueSyntheticAnkita()
        val restarted = buildCoordinator()
        assertTrue(restarted.isWorkspaceUnlocked())
        assertEquals(AuthSessionState.AUTHORIZED_ONLINE, restarted.sessionState())
        restarted.signOut()
        val signedOut = buildCoordinator()
        assertFalse(signedOut.isWorkspaceUnlocked())
        assertEquals(AuthSessionState.LOCKED, signedOut.sessionState())
        assertTrue(signedOut.hasVerifiedOwner())
    }

    @Test
    fun workspaceStartsLockedEvenWithPersistedOwner() = runBlocking {
        coordinator.continueSyntheticAnkita()
        coordinator.signOut()
        val restarted = buildCoordinator()
        assertFalse(restarted.isWorkspaceUnlocked())
        assertEquals(AuthSessionState.LOCKED, restarted.sessionState())
        assertTrue(restarted.hasVerifiedOwner())
    }

    @Test
    fun unprovisionedGoogleIdentityDoesNotBind() = runBlocking {
        gateway.bootstrapHandler = { _, _ ->
            pendingBootstrap("sub-a", "user-stranger", "acct-stranger")
        }
        runCatching { coordinator.bindGoogle("jwt", "sub-a", "Dr A") }
            .exceptionOrNull()
            .let { assertTrue(it is UnprovisionedIdentityException) }
        assertNull(bindings.active())
        assertNotNull(tokens.token())
        assertEquals("user-stranger", profiles.latest()?.userId)
        assertFalse(coordinator.isWorkspaceUnlocked())
        assertEquals(AuthSessionState.AUTHENTICATED_UNPROVISIONED, coordinator.sessionState())
    }

    @Test
    fun secondGoogleSubjectCannotInheritOwner() = runBlocking {
        gateway.bootstrapHandler = { token, name ->
            authorizedBootstrap(
                subject = if (token == "jwt-a") "sub-a" else "sub-b",
                displayName = name
            )
        }
        coordinator.bindGoogle("jwt-a", "sub-a", "Dr A")
        coordinator.signOut()
        val failed = runCatching { coordinator.bindGoogle("jwt-b", "sub-b", "Dr B") }.exceptionOrNull()
        assertTrue(failed is AccountMismatchException)
        assertEquals("sub-a", bindings.active()?.authSubject)
        assertFalse(coordinator.isWorkspaceUnlocked())
        assertEquals(AccountKind.GOOGLE, bindings.active()?.kind)
    }

    @Test
    fun googleDoesNotReuseSyntheticOwner() = runBlocking {
        coordinator.continueSyntheticAnkita()
        coordinator.signOut()
        gateway.bootstrapHandler = { _, name -> authorizedBootstrap("sub-a", name) }
        val failed = runCatching { coordinator.bindGoogle("jwt-a", "sub-a") }.exceptionOrNull()
        assertTrue(failed is AccountMismatchException)
        assertEquals(AccountKind.SYNTHETIC, bindings.active()?.kind)
        assertEquals("synthetic-ankita", bindings.active()?.authSubject)
    }

    private fun buildCoordinator() = AuthCoordinator(
        bindings,
        tokens,
        provider,
        gateway,
        AuthorizedCallback { },
        profiles,
        ProfileSyncCallback { _, _ -> }
    )
}

private fun authorizedBootstrap(subject: String, displayName: String) = GatewayBootstrap(
    accountId = CareLocalSession.OWNER_ACCOUNT_ID,
    userId = "user-a",
    authIssuer = "https://securetoken.google.com/medtrack-6497f",
    authSubject = subject,
    displayName = displayName,
    provisioningState = "PRODUCT_ENABLED",
    accessState = "AUTHORIZED",
    accessLabel = "Pilot access",
    profileJson = JSONObject().put("displayName", displayName).put("version", 1)
)

private fun pendingBootstrap(subject: String, userId: String, accountId: String) = GatewayBootstrap(
    accountId = accountId,
    userId = userId,
    authIssuer = "https://securetoken.google.com/medtrack-6497f",
    authSubject = subject,
    displayName = "Dr A",
    provisioningState = "ACCESS_PENDING",
    accessState = "PENDING",
    accessLabel = "No subscription",
    profileJson = JSONObject().put("displayName", "Dr A").put("version", 1).put("userId", userId)
)

private class RecordingTokenProvider(
    private val store: AuthTokenStore
) : AuthTokenProvider {
    override suspend fun bearerToken(forceRefresh: Boolean): String? = store.token()
    override fun clearSession() {
        store.clear()
    }
}

private class FakeGateway : InferenceGateway {
    var bootstrapHandler: (String, String) -> GatewayBootstrap = { _, _ ->
        throw GatewayException("UNAVAILABLE", "not configured")
    }

    override fun capabilities(token: String): GatewayCapabilities {
        throw GatewayException("UNAVAILABLE", "unused")
    }

    override fun registerDevice(token: String, deviceId: String, replaceExisting: Boolean): JSONObject =
        JSONObject().put("deviceId", deviceId).put("status", "ACTIVE")

    override fun submitJob(token: String, body: JSONObject, fixture: String?): GatewayJob {
        throw GatewayException("UNAVAILABLE", "unused")
    }

    override fun getJob(token: String, jobId: String): GatewayJob {
        throw GatewayException("UNAVAILABLE", "unused")
    }

    override fun cancelJob(token: String, jobId: String): GatewayJob {
        throw GatewayException("UNAVAILABLE", "unused")
    }

    override fun bootstrapProfile(token: String, displayName: String): GatewayBootstrap =
        bootstrapHandler(token, displayName)

    override fun getProfile(token: String): JSONObject = JSONObject()
    override fun patchProfile(token: String, body: JSONObject): JSONObject = body
    override fun getAccess(token: String): JSONObject = JSONObject()
    override fun searchSpecialties(token: String, query: String): JSONObject = JSONObject()
    override fun searchHospitals(token: String, query: String): JSONObject = JSONObject()
}
