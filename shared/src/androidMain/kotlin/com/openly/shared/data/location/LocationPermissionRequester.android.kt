package com.openly.shared.data.location

import android.Manifest
import androidx.activity.result.ActivityResultLauncher

/**
 * Android can only show the system permission dialog through a launcher registered by a
 * ComponentActivity before it reaches STARTED — commonMain composables can't do that themselves,
 * so MainActivity registers one and attaches it here after `setContent`.
 */
actual class LocationPermissionRequester actual constructor() {

    private var launcher: ActivityResultLauncher<String>? = null

    fun attachLauncher(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    actual fun request() {
        launcher?.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
