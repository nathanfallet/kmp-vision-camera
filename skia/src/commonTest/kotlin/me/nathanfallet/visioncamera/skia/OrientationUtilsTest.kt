package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.CameraOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

class OrientationUtilsTest {
    @Test
    fun orientationsAreClockwiseDegrees() {
        assertEquals(0, orientationToDegrees(CameraOrientation.UP))
        assertEquals(90, orientationToDegrees(CameraOrientation.RIGHT))
        assertEquals(180, orientationToDegrees(CameraOrientation.DOWN))
        assertEquals(270, orientationToDegrees(CameraOrientation.LEFT))
    }

    @Test
    fun rotationsAreNormalized() {
        assertEquals(0, normalizeRotationDegrees(0))
        assertEquals(0, normalizeRotationDegrees(-0))
        assertEquals(270, normalizeRotationDegrees(-90))
        assertEquals(180, normalizeRotationDegrees(-180))
        assertEquals(90, normalizeRotationDegrees(-270))
        assertEquals(0, normalizeRotationDegrees(360))
        assertEquals(90, normalizeRotationDegrees(450))
        assertEquals(270, normalizeRotationDegrees(-450))
    }

    @Test
    fun counterRotationsCancelTheOrientation() {
        CameraOrientation.entries.forEach { orientation ->
            val rotation = orientationToDegrees(orientation)
            assertEquals(0, normalizeRotationDegrees(rotation + normalizeRotationDegrees(-rotation)))
        }
    }
}
