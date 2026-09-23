package com.medtrack.app.hybrid.account

import com.medtrack.app.BuildConfig
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.hybrid.gateway.GatewayBootstrap
import com.medtrack.app.hybrid.gateway.GatewayException
import com.medtrack.app.hybrid.gateway.InferenceGateway
import com.medtrack.app.hybrid.profile.ProfileCache
import com.medtrack.app.hybrid.profile.ProfileSyncCallback
import com.medtrack.app.hybrid.request.AuthTokenProvider
import com.medtrack.app.hybrid.request.AuthTokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun interface AuthorizedCallback {
    suspend fun onAuthorized()
}

@Singleton
class AuthCoordinator @Inject constructor(
    private val bindingStore: AccountBindingStore,
    private val tokenStore: AuthTokenStore,
    private val tokenProvider: AuthTokenProvider,
    private val gateway: InferenceGateway,
    private val afterAuthorize: AuthorizedCallback,
    private val profileCache: ProfileCache,
    private val profileSync: ProfileSyncCallback
) {
    @Volatile
    private var sessionState: AuthSessionState = AuthSessionState.LOCKED

    init {
        restorePersistedSession()
    }

    fun current(): BoundAccount? = bindingStore.active()

    fun sessionState(): AuthSessionState = sessionState

    fun isWorkspaceUnlocked(): Boolean =
        sessionState == AuthSessionState.AUTHORIZED_ONLINE ||
            sessionState == AuthSessionState.AUTHORIZED_OFFLINE

    fun hasVerifiedOwner(): Boolean = current() != null

    fun isSyntheticOwner(): Boolean = current()?.kind == AccountKind.SYNTHETIC

    fun isGoogleOwner(): Boolean = current()?.kind == AccountKind.GOOGLE

    fun canOfflineUnlock(): Boolean {
        val bound = current() ?: return false
        return if (bound.kind == AccountKind.SYNTHETIC) {
            BuildConfig.DEBUG_AUTH_BYPASS
        } else {
            true
        }
    }

    fun debugBypassAllowed(): Boolean =
        BuildConfig.DEBUG_AUTH_BYPASS && current()?.kind != AccountKind.GOOGLE

    suspend fun continueSyntheticAnkita() {
        if (current()?.kind == AccountKind.GOOGLE) {
            throw AccountMismatchException(
                "This device already holds a Google workspace. Synthetic debug access is disabled."
            )
        }
        sessionState = AuthSessionState.AUTHENTICATING
        val deviceId = bindingStore.deviceId()
        val token = DevToken.encode("medtrack-test", "synthetic-ankita", deviceId)
        tokenStore.save(token)
        val bound = BoundAccount(
            userId = "user-ankita",
            accountId = CareLocalSession.OWNER_ACCOUNT_ID,
            ownerAccountId = CareLocalSession.OWNER_ACCOUNT_ID,
            actorPersonId = CareLocalSession.ACTOR_PERSON_ID,
            authIssuer = "medtrack-test",
            authSubject = "synthetic-ankita",
            deviceId = deviceId,
            displayName = "Dr. Ankita",
            onlineAuthorized = true,
            kind = AccountKind.SYNTHETIC
        )
        bindingStore.bind(bound)
        authorize(AuthSessionState.AUTHORIZED_ONLINE)
        withContext(Dispatchers.IO) {
            runCatching {
                val boot = gateway.bootstrapProfile(token, "Dr. Ankita")
                cacheBootstrap(boot)
                profileSync.onProfileConfirmed(bound, boot.profileJson)
            }
            runCatching { gateway.registerDevice(token, deviceId) }
            runCatching { afterAuthorize.onAuthorized() }
        }
    }

    suspend fun bindGoogle(
        idToken: String,
        subject: String,
        displayName: String = "Signed-in clinician"
    ) {
        sessionState = AuthSessionState.AUTHENTICATING
        val existing = bindingStore.active()
        if (existing?.kind == AccountKind.SYNTHETIC) {
            sessionState = AuthSessionState.LOCKED
            throw AccountMismatchException(
                "This device still has a synthetic debug workspace. Google sign-in cannot open those records."
            )
        }
        if (existing != null && existing.authSubject.isNotBlank() && existing.authSubject != subject) {
            sessionState = AuthSessionState.LOCKED
            throw AccountMismatchException(
                "This device already holds records for a different Google account."
            )
        }
        val deviceId = bindingStore.deviceId()
        val issuer = firebaseIssuer()
        try {
            val boot = withContext(Dispatchers.IO) {
                runCatching { gateway.bootstrapProfile(idToken, displayName) }
                    .getOrElse { error ->
                        if (error is GatewayException) throw mapGateway(error, existing, subject)
                        throw error
                    }
            }
            cacheBootstrap(boot)
            if (boot.authSubject.isNotBlank() && boot.authSubject != subject) {
                sessionState = AuthSessionState.LOCKED
                throw AuthorizationDeniedException("Signed-in subject does not match the provisioned identity.")
            }
            val entitled = boot.accessState == "AUTHORIZED" && boot.provisioningState == "PRODUCT_ENABLED"
            if (!entitled) {
                tokenStore.save(idToken)
                sessionState = AuthSessionState.AUTHENTICATED_UNPROVISIONED
                throw UnprovisionedIdentityException(
                    "This Google account has a cloud profile, but MedTrack care and AI are not enabled for it."
                )
            }
            val owner = boot.accountId.trim()
            if (owner.isBlank() || boot.userId.isBlank() || boot.authSubject.isBlank()) {
                sessionState = AuthSessionState.LOCKED
                error("Gateway bootstrap omitted account identity.")
            }
            if (existing != null && existing.ownerAccountId != owner) {
                sessionState = AuthSessionState.LOCKED
                throw AccountMismatchException(
                    "This device already holds records for a different account. " +
                        "Sign-in alone does not open another user's database."
                )
            }
            tokenStore.save(idToken)
            try {
                withContext(Dispatchers.IO) {
                    gateway.registerDevice(idToken, deviceId, replaceExisting = false)
                }
            } catch (error: Exception) {
                tokenStore.clear()
                throw error
            }
            val actor = if (owner == CareLocalSession.OWNER_ACCOUNT_ID) {
                CareLocalSession.ACTOR_PERSON_ID
            } else {
                existing?.actorPersonId ?: subject
            }
            val bound = BoundAccount(
                userId = boot.userId,
                accountId = owner,
                ownerAccountId = owner,
                actorPersonId = actor,
                authIssuer = boot.authIssuer.ifBlank { issuer },
                authSubject = boot.authSubject,
                deviceId = deviceId,
                displayName = boot.displayName.ifBlank { displayName },
                onlineAuthorized = true,
                kind = AccountKind.GOOGLE
            )
            bindingStore.bind(bound)
            authorize(AuthSessionState.AUTHORIZED_ONLINE)
            withContext(Dispatchers.IO) {
                runCatching { profileSync.onProfileConfirmed(bound, boot.profileJson) }
                runCatching { afterAuthorize.onAuthorized() }
            }
        } catch (denied: UnprovisionedIdentityException) {
            sessionState = AuthSessionState.AUTHENTICATED_UNPROVISIONED
            throw denied
        } catch (mismatch: AccountMismatchException) {
            sessionState = AuthSessionState.LOCKED
            throw mismatch
        } catch (denied: AuthorizationDeniedException) {
            sessionState = AuthSessionState.LOCKED
            throw denied
        } catch (unavailable: GatewayUnavailableException) {
            sessionState = AuthSessionState.LOCKED
            if (existing != null && existing.kind == AccountKind.GOOGLE && existing.authSubject == subject) {
                throw GatewayUnavailableException(
                    "The gateway is unreachable. Unlock local records with the device credential."
                )
            }
            throw GatewayUnavailableException(
                "The gateway could not authorize this device. First-time access requires a successful provisioning check."
            )
        } catch (gateway: GatewayException) {
            sessionState = when (gateway.code) {
                "UNPROVISIONED" -> AuthSessionState.AUTHENTICATED_UNPROVISIONED
                else -> AuthSessionState.LOCKED
            }
            throw mapGateway(gateway, existing, subject)
        }
    }

    fun unlockOffline() {
        val current = bindingStore.active() ?: return
        if (current.kind == AccountKind.SYNTHETIC && !BuildConfig.DEBUG_AUTH_BYPASS) {
            sessionState = AuthSessionState.LOCKED
            return
        }
        bindingStore.bind(current.copy(onlineAuthorized = false))
        authorize(AuthSessionState.AUTHORIZED_OFFLINE)
    }

    fun signOut() {
        tokenProvider.clearSession()
        tokenStore.clear()
        bindingStore.clearOnlineSession()
        bindingStore.setSessionOpen(false)
        sessionState = AuthSessionState.LOCKED
    }

    private fun authorize(state: AuthSessionState) {
        sessionState = state
        bindingStore.setSessionOpen(true)
    }

    private fun restorePersistedSession() {
        val bound = bindingStore.active() ?: return
        if (!bindingStore.isSessionOpen()) return
        sessionState = if (bound.onlineAuthorized) {
            AuthSessionState.AUTHORIZED_ONLINE
        } else {
            AuthSessionState.AUTHORIZED_OFFLINE
        }
    }

    private fun cacheBootstrap(boot: GatewayBootstrap) {
        profileCache.saveConfirmed(
            accountId = boot.accountId,
            userId = boot.userId,
            email = boot.email,
            profileJson = boot.profileJson,
            accessJson = JSONObject()
                .put(
                    "entitlement",
                    JSONObject()
                        .put("accessState", boot.accessState)
                        .put("label", boot.accessLabel)
                )
        )
    }

    private fun mapGateway(
        error: GatewayException,
        existing: BoundAccount?,
        subject: String?
    ): Exception = when (error.code) {
        "UNPROVISIONED" -> UnprovisionedIdentityException()
        "UNAUTHORIZED", "DEVICE_REPLACEMENT_REQUIRED" ->
            AuthorizationDeniedException(error.message ?: error.code)
        "UNAUTHENTICATED" -> AuthorizationDeniedException(error.message ?: "Authentication failed.")
        "TRANSIENT_PROVIDER", "UNAVAILABLE" -> GatewayUnavailableException(error.message ?: error.code)
        else -> error
    }

    private fun firebaseIssuer(): String {
        val project = BuildConfig.FIREBASE_PROJECT_ID.trim()
        return "https://securetoken.google.com/$project"
    }
}

object DevToken {
    fun encode(issuer: String, subject: String, deviceId: String): String {
        val payload = org.json.JSONObject()
            .put("iss", issuer)
            .put("sub", subject)
            .put("exp", System.currentTimeMillis() / 1000 + 3600)
            .put("device_id", deviceId)
            .toString()
        val encoded = android.util.Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        return "mt-dev.$encoded"
    }
}
