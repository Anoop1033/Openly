package com.openly.shared.data.location

/**
 * Triggers the OS location-permission dialog.
 *
 * The result isn't returned here — Android and iOS surface it completely differently (a launcher
 * callback vs. a delegate method), so callers just re-poll [LocationTracker.hasLocationPermission]
 * after calling [request] instead of both platforms pretending to share one callback shape.
 */
expect class LocationPermissionRequester() {
    fun request()
}
