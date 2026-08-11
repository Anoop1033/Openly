package com.openly.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.openly.app.data.model.UserProfile
import com.openly.app.data.remote.FirestorePaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfileRepository(private val firestore: FirebaseFirestore) {

    private val users get() = firestore.collection(FirestorePaths.USERS)

    suspend fun getProfile(uid: String): UserProfile? =
        users.document(uid).get().await().toObject(UserProfile::class.java)

    suspend fun saveProfile(profile: UserProfile) {
        users.document(profile.uid).set(profile).await()
    }

    fun observeOwnProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(UserProfile::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun setOpenStatus(uid: String, isOpen: Boolean) {
        users.document(uid).set(
            mapOf("isOpen" to isOpen, "lastActiveMillis" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
    }

    suspend fun updateLocation(uid: String, lat: Double, lng: Double) {
        users.document(uid).set(
            mapOf("lat" to lat, "lng" to lng, "lastActiveMillis" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
    }
}
