package com.medtrack.app.hybrid.account

import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.command.CareWorkspace
import javax.inject.Inject
import javax.inject.Singleton

enum class AccountKind {
    SYNTHETIC,
    GOOGLE
}

enum class AuthSessionState {
    LOCKED,
    AUTHENTICATING,
    AUTHENTICATED_UNPROVISIONED,
    AUTHORIZED_ONLINE,
    AUTHORIZED_OFFLINE
}

data class BoundAccount(
    val userId: String,
    val accountId: String,
    val ownerAccountId: String,
    val actorPersonId: String,
    val authIssuer: String,
    val authSubject: String,
    val deviceId: String,
    val displayName: String,
    val onlineAuthorized: Boolean,
    val kind: AccountKind
)

@Singleton
class AccountSession @Inject constructor(
    private val bindingStore: AccountBindingStore
) {
    fun active(): BoundAccount? = bindingStore.active()

    fun isUnlocked(): Boolean = false

    fun ownerAccountId(): String =
        active()?.ownerAccountId ?: CareLocalSession.OWNER_ACCOUNT_ID

    fun workspace(): CareWorkspace {
        val bound = active()
        return if (bound != null) {
            CareWorkspace(bound.ownerAccountId, bound.actorPersonId)
        } else {
            CareLocalSession.workspace()
        }
    }

    fun timeZone(): String = CareLocalSession.TIME_ZONE

    fun hospitalId(): String = CareLocalSession.HOSPITAL_ID

    fun deviceId(): String = active()?.deviceId ?: bindingStore.deviceId()
}
