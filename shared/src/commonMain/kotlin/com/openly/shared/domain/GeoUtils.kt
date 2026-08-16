package com.openly.shared.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val METERS_PER_DEGREE_LAT = 111_320.0

// java.lang.Math isn't available outside the JVM, so degree/radian conversion is done by hand
// to keep this file pure Kotlin and usable from both Android and iOS.
private fun Double.toRadians(): Double = this * PI / 180.0
private fun Double.toDegrees(): Double = this * 180.0 / PI

data class GeoBounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

object GeoUtils {

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1).toRadians()
        val dLng = (lng2 - lng1).toRadians()
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /** Initial compass bearing from point 1 to point 2, in degrees clockwise from north [0, 360). */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = lat1.toRadians()
        val phi2 = lat2.toRadians()
        val dLng = (lng2 - lng1).toRadians()
        val y = sin(dLng) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLng)
        val theta = atan2(y, x)
        return (theta.toDegrees() + 360) % 360
    }

    /** A square lat/lng box roughly `radiusMeters` out from the center, for a cheap Firestore range query. */
    fun boundingBox(lat: Double, lng: Double, radiusMeters: Double): GeoBounds {
        val latDelta = radiusMeters / METERS_PER_DEGREE_LAT
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(lat.toRadians()).coerceAtLeast(0.01)
        val lngDelta = radiusMeters / metersPerDegreeLng
        return GeoBounds(
            minLat = lat - latDelta,
            maxLat = lat + latDelta,
            minLng = min(lng - lngDelta, lng + lngDelta),
            maxLng = max(lng - lngDelta, lng + lngDelta)
        )
    }
}
