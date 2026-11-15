package me.nathanfallet.visioncamera.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationManagerOptionsTest {
    @Test
    fun defaultsMatchVisionCamera() {
        val options = LocationManagerOptions()
        assertEquals(LocationAccuracy.BALANCED, options.accuracy)
        assertEquals(10.0, options.distanceFilter)
        assertEquals(10_000, options.updateInterval)
    }

    @Test
    fun createLocationKeepsTheCoordinates() {
        val location = createLocation(48.8566, 2.3522)
        assertEquals(48.8566, location.latitude)
        assertEquals(2.3522, location.longitude)
        assertTrue(location.timestamp > 0.0)
    }
}
