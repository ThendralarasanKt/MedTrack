package com.medtrack.app.hybrid.account

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.medtrack.app.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class GoogleAuthResult(
    val firebaseIdToken: String,
    val subject: String,
    val displayName: String
)

class GoogleSignInCancelled : Exception("Google sign-in was cancelled")

class GoogleSignInHelper(
    private val activity: Activity,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun signIn(): GoogleAuthResult {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            error("Google Sign-In is not configured. google-services.json needs a web OAuth client.")
        }
        val googleIdToken = requestGoogleIdToken(webClientId)
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val user = firebaseAuth.signInWithCredential(credential).awaitResult().user
            ?: error("Firebase sign-in returned no user.")
        val firebaseToken = user.getIdToken(false).awaitResult().token
            ?: error("Firebase did not issue an ID token.")
        return GoogleAuthResult(
            firebaseIdToken = firebaseToken,
            subject = user.uid,
            displayName = user.displayName?.ifBlank { null } ?: user.email ?: "Signed-in clinician"
        )
    }

    private suspend fun requestGoogleIdToken(webClientId: String): String {
        val manager = CredentialManager.create(activity)
        return try {
            idTokenFrom(
                manager.getCredential(
                    activity,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setServerClientId(webClientId)
                                .setFilterByAuthorizedAccounts(false)
                                .setAutoSelectEnabled(false)
                                .build()
                        )
                        .build()
                ).credential
            )
        } catch (_: NoCredentialException) {
            try {
                idTokenFrom(
                    manager.getCredential(
                        activity,
                        GetCredentialRequest.Builder()
                            .addCredentialOption(
                                GetSignInWithGoogleOption.Builder(webClientId).build()
                            )
                            .build()
                    ).credential
                )
            } catch (cancelled: GetCredentialCancellationException) {
                throw GoogleSignInCancelled()
            }
        } catch (cancelled: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        }
    }

    private fun idTokenFrom(credential: androidx.credentials.Credential): String {
        val google = if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data)
        } else {
            error("Unexpected credential type: ${credential.type}")
        }
        return google.idToken
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isCancelled) return@addOnCompleteListener
            val error = task.exception
            if (error != null) {
                continuation.resumeWithException(error)
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            }
        }
    }
