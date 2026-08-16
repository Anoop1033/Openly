package com.openly.shared.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth

/**
 * Anonymous auth by design: someone spotting the radar across a cafe should be able to open the
 * app and appear on it in seconds, with no signup screen in the way.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously()
        return requireNotNull(result.user).uid
    }
}
