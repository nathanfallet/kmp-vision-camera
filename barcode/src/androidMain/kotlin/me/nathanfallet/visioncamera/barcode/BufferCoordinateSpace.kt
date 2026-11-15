package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Point

/**
 * The pixel coordinate space barcodes are reported in, converting ML Kit's coordinates into it.
 *
 * ML Kit is given the input's rotation (for detection quality), and so reports coordinates in the **rotated**
 * (upright) image. Barcodes are reported relative to their input instead:
 * - for a `Frame` (or a buffer of the Barcode Scanner output), the frame's own coordinate system: its raw
 *   (un-rotated) [width]x[height] pixel buffer, which `Frame.convertFramePointToCameraPoint` expects (it inverts the
 *   `ImageProxy`'s `sensorToBufferTransformMatrix`). The [rotationDegrees] is then the frame's `rotationDegrees`.
 * - for an `Image`, the image's coordinate system: ML Kit is given no rotation, so [rotationDegrees] is `0`.
 *
 * This is the Android equivalent of the iOS `VisionCoordinateSpace`.
 */
internal class BufferCoordinateSpace(
    /** The width of the buffer, in pixels. */
    val width: Int,
    /** The height of the buffer, in pixels. */
    val height: Int,
    /**
     * The clockwise rotation, in degrees (a multiple of 90), ML Kit applied to the buffer to get the image it
     * scanned (CameraX's `ImageInfo.rotationDegrees`).
     */
    val rotationDegrees: Int,
) {
    /** Converts a point of the rotated image ML Kit scanned to this buffer space. */
    fun convert(x: Double, y: Double): Point {
        val width = width.toDouble()
        val height = height.toDouble()
        // Inverse of the clockwise rotation of the buffer by `rotationDegrees`
        return when (Math.floorMod(rotationDegrees, FULL_TURN)) {
            QUARTER_TURN -> Point(x = y, y = height - x)
            HALF_TURN -> Point(x = width - x, y = height - y)
            THREE_QUARTER_TURN -> Point(x = width - y, y = x)
            else -> Point(x = x, y = y)
        }
    }

    /** Converts a rectangle of the rotated image ML Kit scanned to this buffer space. */
    fun convert(left: Double, top: Double, right: Double, bottom: Double): Rect {
        // A rotation by a multiple of 90° keeps a rectangle axis-aligned: convert two opposite corners.
        val first = convert(left, top)
        val second = convert(right, bottom)
        return Rect(
            left = minOf(first.x, second.x),
            right = maxOf(first.x, second.x),
            top = minOf(first.y, second.y),
            bottom = maxOf(first.y, second.y),
        )
    }

    private companion object {
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val THREE_QUARTER_TURN = 270
        const val FULL_TURN = 360
    }
}
