package com.openly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `distance between identical points is zero`() {
        assertEquals(0.0, GeoUtils.distanceMeters(12.9716, 77.5946, 12.9716, 77.5946), 0.001)
    }

    @Test
    fun `distance for one degree of longitude at the equator is about 111km`() {
        val meters = GeoUtils.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_320.0, meters, 500.0)
    }

    @Test
    fun `bearing due north is zero degrees`() {
        assertEquals(0.0, GeoUtils.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.5)
    }

    @Test
    fun `bearing due east is ninety degrees`() {
        assertEquals(90.0, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test
    fun `bearing due south is one hundred eighty degrees`() {
        assertEquals(180.0, GeoUtils.bearingDegrees(0.0, 0.0, -1.0, 0.0), 0.5)
    }

    @Test
    fun `bearing due west is two hundred seventy degrees`() {
        assertEquals(270.0, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, -1.0), 0.5)
    }

    @Test
    fun `bounding box contains the center point`() {
        val bounds = GeoUtils.boundingBox(12.9716, 77.5946, 200.0)
        assertTrue(bounds.minLat < 12.9716 && 12.9716 < bounds.maxLat)
        assertTrue(bounds.minLng < 77.5946 && 77.5946 < bounds.maxLng)
    }
}
