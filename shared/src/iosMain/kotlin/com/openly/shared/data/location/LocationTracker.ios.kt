package com.openly.shared.data.location

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

/**
 * CoreLocation-backed counterpart of the Android FusedLocationProvider implementation.
 *
 * NOTE: this has never been compiled or run — building for iOS requires macOS + Xcode, which the
 * machine this was written on does not have. Treat it as a first draft to verify on a Mac.
 * Info.plist must declare NSLocationWhenInUseUsageDescription or CoreLocation stays silent.
 */
actual class LocationTracker {

    private val manager = CLLocationManager()

    actual val hasLocationPermission: Boolean
        get() = when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> true
            else -> false
        }

    fun requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    actual fun locationUpdates(intervalMillis: Long): Flow<GeoPosition> = callbackFlow {
        if (!hasLocationPermission) {
            manager.requestWhenInUseAuthorization()
        }

        // CoreLocation pushes updates as the device moves rather than on a fixed interval, so
        // intervalMillis is expressed as a distance filter instead: roughly "tell me when the fix
        // is meaningfully different", which is what the radar actually cares about.
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                location.coordinate.useContents {
                    trySend(GeoPosition(latitude, longitude))
                }
            }

            override fun locationManager(
                manager: CLLocationManager,
                didChangeAuthorizationStatus: CLAuthorizationStatus
            ) {
                if (hasLocationPermission) manager.startUpdatingLocation()
            }
        }

        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 10.0
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }
}
