package com.openly.app.data.model

/** A profile positioned on the radar, computed client-side from both users' live coordinates. */
data class NearbyUser(
    val profile: UserProfile,
    val distanceMeters: Double,
    val bearingDegrees: Double
)
