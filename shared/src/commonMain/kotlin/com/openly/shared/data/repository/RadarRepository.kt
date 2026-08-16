package com.openly.shared.data.repository

import com.openly.shared.data.model.NearbyUser
import com.openly.shared.data.model.UserProfile
import com.openly.shared.data.remote.FirestorePaths
import com.openly.shared.domain.GeoUtils
import com.openly.shared.platform.nowMillis
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    ): Flow<List<NearbyUser>> {
        val bounds = GeoUtils.boundingBox(selfLat, selfLng, radiusMeters)

        return firestore.collection(FirestorePaths.USERS)
            .where {
                all(
                    "isOpen" equalTo true,
                    "lat" greaterThanOrEqualTo bounds.minLat,
                    "lat" lessThanOrEqualTo bounds.maxLat
                )
            }
            .orderBy("lat", Direction.ASCENDING)
            .snapshots
            .map { snapshot ->
                val now = nowMillis()
                snapshot.documents
                    .mapNotNull { runCatching { it.data<UserProfile>() }.getOrNull() }
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
            }
    }
}
