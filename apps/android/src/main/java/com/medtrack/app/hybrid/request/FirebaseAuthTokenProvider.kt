package com.medtrack.app.hybrid.request

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface AuthTokenProvider {
    suspend fun bearerToken(forceRefresh: Boolean = false): String?
    fun clearSession()
}

@Singleton
class FirebaseAuthTokenProvider @Inject constructor(
    private val store: AuthTokenStore
) : AuthTokenProvider {
    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    override suspend fun bearerToken(forceRefresh: Boolean): String? {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val token = user.getIdToken(forceRefresh).awaitResult().token
            if (!token.isNullOrBlank()) {
                store.save(token)
                return token
            }
        }
        return store.token()
    }

    override fun clearSession() {
        store.clear()
        runCatching { firebaseAuth.signOut() }
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
