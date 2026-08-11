package com.openly.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(private val context: Context) {

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Emits nothing if location permission hasn't been granted yet — callers should check first. */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long = 15_000L): Flow<Location> = callbackFlow {
        if (!hasLocationPermission) {
            close()
            return@callbackFlow
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)
        client.lastLocation.addOnSuccessListener { it?.let { loc -> trySend(loc) } }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
