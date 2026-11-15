package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformPointTest {
    private val point = Point(0.25, 0.1)

    @Test
    fun upKeepsThePoint() {
        assertEquals(point, transformPoint(point, Transform(CameraOrientation.UP, isMirrored = false)))
    }

    @Test
    fun downRotatesByHalfATurn() {
        assertEquals(Point(0.75, 0.9), transformPoint(point, Transform(CameraOrientation.DOWN, isMirrored = false)))
    }

    @Test
    fun leftRotates() {
        assertEquals(Point(0.1, 0.75), transformPoint(point, Transform(CameraOrientation.LEFT, isMirrored = false)))
    }

    @Test
    fun rightRotates() {
        assertEquals(Point(0.9, 0.25), transformPoint(point, Transform(CameraOrientation.RIGHT, isMirrored = false)))
    }

    @Test
    fun mirroredFlipsXBeforeRotating() {
        assertEquals(Point(0.75, 0.1), transformPoint(point, Transform(CameraOrientation.UP, isMirrored = true)))
        assertEquals(Point(0.9, 0.75), transformPoint(point, Transform(CameraOrientation.RIGHT, isMirrored = true)))
        assertEquals(Point(0.1, 0.25), transformPoint(point, Transform(CameraOrientation.LEFT, isMirrored = true)))
        assertEquals(Point(0.25, 0.9), transformPoint(point, Transform(CameraOrientation.DOWN, isMirrored = true)))
    }
}
