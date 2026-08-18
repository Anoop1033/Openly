package com.openly.shared.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Anonymous auth by design: someone spotting the radar across a cafe should be able to open the
 * app and appear on it in seconds, with no signup screen in the way.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    val currentUid: String? get() = auth.currentUser?.uid

    private val _sessionGeneration = MutableStateFlow(0)

    /**
     * Incremented whenever the signed-in identity is torn down. Deleting the account leaves the old
     * uid unusable, and because the uid is held in composition state nothing else would tell the UI
     * to sign in again — it would keep issuing writes as a user that no longer exists.
     */
    val sessionGeneration: StateFlow<Int> = _sessionGeneration.asStateFlow()

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously()
        return requireNotNull(result.user).uid
    }

    /**
     * Anonymous users may delete themselves without re-authenticating, which is what makes an
     * in-app "delete my account" possible when there is no sign-in flow to send anyone back through.
     */
    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()
        _sessionGeneration.value += 1
    }
}
