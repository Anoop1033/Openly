package com.openly.shared.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class LocationTracker(private val context: Context) {

    actual val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    actual fun locationUpdates(intervalMillis: Long): Flow<GeoPosition> = callbackFlow {
        if (!hasLocationPermission) {
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(GeoPosition(it.latitude, it.longitude)) }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)
        client.lastLocation.addOnSuccessListener { loc ->
            loc?.let { trySend(GeoPosition(it.latitude, it.longitude)) }
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
