package com.openly.shared.data.repository

import com.openly.shared.data.model.UserProfile
import com.openly.shared.data.remote.FirestorePaths
import com.openly.shared.platform.nowMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val firestore: FirebaseFirestore) {

    private val users get() = firestore.collection(FirestorePaths.USERS)

    suspend fun getProfile(uid: String): UserProfile? =
        runCatching { users.document(uid).get().data<UserProfile>() }.getOrNull()

    suspend fun saveProfile(profile: UserProfile) {
        users.document(profile.uid).set(profile)
    }

    fun observeOwnProfile(uid: String): Flow<UserProfile?> =
        users.document(uid).snapshots.map { snapshot ->
            runCatching { snapshot.data<UserProfile>() }.getOrNull()
        }

    suspend fun setOpenStatus(uid: String, isOpen: Boolean) {
        users.document(uid).update("isOpen" to isOpen, "lastActiveMillis" to nowMillis())
    }

    suspend fun updateLocation(uid: String, lat: Double, lng: Double) {
        users.document(uid).update("lat" to lat, "lng" to lng, "lastActiveMillis" to nowMillis())
    }

    /**
     * Best-effort: the token refreshes on reinstall/restore and there is nothing useful to do if
     * this write fails, so a stale token just means one missed push rather than a broken app.
     */
    suspend fun updatePushToken(uid: String, token: String) {
        runCatching { users.document(uid).update("fcmToken" to token) }
    }
}
