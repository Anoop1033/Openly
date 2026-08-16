package com.openly.shared

import com.openly.shared.domain.GeoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun distanceBetweenIdenticalPointsIsZero() {
        assertEquals(0.0, GeoUtils.distanceMeters(12.9716, 77.5946, 12.9716, 77.5946), 0.001)
    }

    @Test
    fun distanceForOneDegreeOfLongitudeAtEquatorIsAbout111km() {
        val meters = GeoUtils.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_320.0, meters, 500.0)
    }

    @Test
    fun bearingDueNorthIsZeroDegrees() {
        assertEquals(0.0, GeoUtils.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.5)
    }

    @Test
    fun bearingDueEastIsNinetyDegrees() {
        assertEquals(90.0, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test
    fun bearingDueSouthIsOneHundredEightyDegrees() {
        assertEquals(180.0, GeoUtils.bearingDegrees(0.0, 0.0, -1.0, 0.0), 0.5)
    }

    @Test
    fun bearingDueWestIsTwoHundredSeventyDegrees() {
        assertEquals(270.0, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, -1.0), 0.5)
    }

    @Test
    fun boundingBoxContainsTheCenterPoint() {
        val bounds = GeoUtils.boundingBox(12.9716, 77.5946, 200.0)
        assertTrue(bounds.minLat < 12.9716 && 12.9716 < bounds.maxLat)
        assertTrue(bounds.minLng < 77.5946 && 77.5946 < bounds.maxLng)
    }
}
