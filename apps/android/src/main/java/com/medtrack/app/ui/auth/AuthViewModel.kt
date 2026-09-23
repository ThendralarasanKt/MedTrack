package com.medtrack.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.hybrid.account.AccountMismatchException
import com.medtrack.app.hybrid.account.AuthCoordinator
import com.medtrack.app.hybrid.account.AuthSessionState
import com.medtrack.app.hybrid.account.AuthorizationDeniedException
import com.medtrack.app.hybrid.account.BoundAccount
import com.medtrack.app.hybrid.account.GatewayUnavailableException
import com.medtrack.app.hybrid.account.UnprovisionedIdentityException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val unlocked: Boolean = false,
    val account: BoundAccount? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val debugBypass: Boolean = false,
    val canOfflineUnlock: Boolean = false,
    val sessionState: AuthSessionState = AuthSessionState.LOCKED,
    val viewingProfile: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val coordinator: AuthCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(
        if (coordinator.isWorkspaceUnlocked()) authorizedState() else lockedState()
    )
    val state = _state.asStateFlow()

    fun continueSynthetic() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching { withContext(Dispatchers.IO) { coordinator.continueSyntheticAnkita() } }
                .onSuccess { emitAuthorized() }
                .onFailure { error ->
                    _state.value = lockedState(error.message ?: "Could not bind the local account.")
                }
        }
    }

    fun requestOfflineUnlock() {
        if (!coordinator.canOfflineUnlock()) {
            _state.value = _state.value.copy(
                error = "Offline unlock is only available for a previously verified owner."
            )
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
    }

    fun onLocalUnlockVerified() {
        coordinator.unlockOffline()
        emitAuthorized()
    }

    fun onLocalUnlockCancelled() {
        _state.value = lockedState()
    }

    fun openProfile() {
        _state.value = _state.value.copy(viewingProfile = true, error = null)
    }

    fun closeProfile() {
        _state.value = lockedState(_state.value.error)
    }

    fun signOut() {
        coordinator.signOut()
        _state.value = lockedState()
    }

    fun beginGoogleSignIn() {
        _state.value = _state.value.copy(
            busy = true,
            error = null,
            sessionState = AuthSessionState.AUTHENTICATING
        )
    }

    fun onGoogleFailure(message: String) {
        _state.value = lockedState(message)
    }

    fun onGoogleToken(idToken: String, subject: String, displayName: String = "Signed-in clinician") {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { coordinator.bindGoogle(idToken, subject, displayName) }
            }.onSuccess {
                emitAuthorized()
            }.onFailure { error ->
                val message = when (error) {
                    is AccountMismatchException,
                    is UnprovisionedIdentityException,
                    is AuthorizationDeniedException,
                    is GatewayUnavailableException -> error.message
                    else -> error.message ?: "Google sign-in could not be completed."
                }
                // Stay on the sign-in screen with the message; My Profile is optional via the button.
                _state.value = lockedState(message)
            }
        }
    }

    private fun emitAuthorized() {
        _state.value = authorizedState()
    }

    private fun authorizedState() = AuthUiState(
        unlocked = coordinator.isWorkspaceUnlocked(),
        account = coordinator.current(),
        debugBypass = coordinator.debugBypassAllowed(),
        canOfflineUnlock = coordinator.canOfflineUnlock(),
        sessionState = coordinator.sessionState()
    )

    private fun lockedState(error: String? = null) = AuthUiState(
        unlocked = false,
        account = coordinator.current(),
        error = error,
        debugBypass = coordinator.debugBypassAllowed(),
        canOfflineUnlock = coordinator.canOfflineUnlock(),
        sessionState = coordinator.sessionState()
    )
}
