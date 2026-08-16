package com.openly.shared.data.location

import kotlinx.coroutines.flow.Flow

/** A position fix, reduced to just what the radar needs. */
data class GeoPosition(val latitude: Double, val longitude: Double)

/**
 * Device location, backed by FusedLocationProvider on Android and CoreLocation on iOS.
 *
 * The radar only ever needs a coordinate pair, so the platform Location/CLLocation types stay
 * behind this boundary rather than leaking into the shared layer.
 */
expect class LocationTracker {
    val hasLocationPermission: Boolean

    /** Emits nothing if location permission hasn't been granted yet — callers should check first. */
    fun locationUpdates(intervalMillis: Long = 15_000L): Flow<GeoPosition>
}
