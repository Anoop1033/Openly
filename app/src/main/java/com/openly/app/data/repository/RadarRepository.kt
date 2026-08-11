package com.openly.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.openly.app.data.model.NearbyUser
import com.openly.app.data.model.UserProfile
import com.openly.app.data.remote.FirestorePaths
import com.openly.app.domain.GeoUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A profile counts as live on the radar if it pinged within this window. */
private const val PRESENCE_STALE_AFTER_MILLIS = 5 * 60 * 1000L

class RadarRepository(private val firestore: FirebaseFirestore) {

    /**
     * Firestore only allows a range filter on one field per query, so this narrows by latitude
     * server-side, then finishes the longitude box and exact-circle distance filter client-side.
     */
    fun observeNearbyOpenUsers(
        selfUid: String,
        selfLat: Double,
        selfLng: Double,
        radiusMeters: Double
    ): Flow<List<NearbyUser>> = callbackFlow {
        val bounds = GeoUtils.boundingBox(selfLat, selfLng, radiusMeters)

        val query = firestore.collection(FirestorePaths.USERS)
            .whereEqualTo("isOpen", true)
            .whereGreaterThanOrEqualTo("lat", bounds.minLat)
            .whereLessThanOrEqualTo("lat", bounds.maxLat)
            .orderBy("lat", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()
            val nearby = snapshot?.documents.orEmpty()
                .mapNotNull { it.toObject(UserProfile::class.java) }
                .filter { profile ->
                    profile.uid != selfUid &&
                        profile.isOpen &&
                        profile.hasLocation &&
                        now - profile.lastActiveMillis < PRESENCE_STALE_AFTER_MILLIS &&
                        profile.lng!! in bounds.minLng..bounds.maxLng
                }
                .map { profile ->
                    NearbyUser(
                        profile = profile,
                        distanceMeters = GeoUtils.distanceMeters(selfLat, selfLng, profile.lat!!, profile.lng!!),
                        bearingDegrees = GeoUtils.bearingDegrees(selfLat, selfLng, profile.lat!!, profile.lng!!)
                    )
                }
                .filter { it.distanceMeters <= radiusMeters }
                .sortedBy { it.distanceMeters }

            trySend(nearby)
        }

        awaitClose { registration.remove() }
    }
}
