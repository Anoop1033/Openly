package com.openly.shared.data.location

import platform.CoreLocation.CLLocationManager

/**
 * iOS can request authorization directly with no host-app wiring, unlike Android's launcher
 * dance — this class exists mainly so common code has one shared call shape for both platforms.
 *
 * NOTE: never compiled — iOS builds require macOS + Xcode. Verify on a Mac.
 */
actual class LocationPermissionRequester actual constructor() {

    private val manager = CLLocationManager()

    actual fun request() {
        manager.requestWhenInUseAuthorization()
    }
}
