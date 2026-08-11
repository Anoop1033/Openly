package com.openly.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Anonymous auth by design: someone spotting the radar across a cafe should be able to open the
 * app and appear on it in seconds, with no signup screen in the way.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        val existing = auth.currentUser
        if (existing != null) return existing.uid
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user).uid
    }
}
