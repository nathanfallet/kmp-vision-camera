package me.nathanfallet.visioncamera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteOrder

internal actual fun createPlatformFrameConverter(): FrameConverter = AndroidFrameConverter()

/**
 * The Android [FrameConverter] (port of V5's `HybridFrameConverter.kt`), converting the CameraX [ImageProxy] of a
 * [NativeFrame] to a [Bitmap].
 */
internal class AndroidFrameConverter : FrameConverter {
    override fun convertFrameToImage(frame: Frame): Image {
        val nativeFrame = frame as? NativeFrame
            ?: throw VisionCameraException("The given `Frame` is not of type `NativeFrame`!")
        if (!frame.isValid) throw VisionCameraException("Cannot convert an already disposed Frame to an Image!")
        val bitmap = nativeFrame.image.toCameraXBitmap()
        return AndroidImage(bitmap.transformed(frame.orientation, frame.isMirrored))
    }

    override fun convertDepthToImage(depth: Depth): Image {
        val nativeFrame = depth as? NativeFrame
            ?: throw VisionCameraException("The given `Depth` is not of type `NativeFrame`!")
        if (!depth.isValid) throw VisionCameraException("Cannot convert an already disposed Depth to an Image!")
        val image = nativeFrame.image
        val bitmap = if (image.format == ImageFormat.DEPTH16) image.depth16ToBitmap() else image.toCameraXBitmap()
        return AndroidImage(bitmap.transformed(depth.orientation, depth.isMirrored))
    }
}

/**
 * Converts this [ImageProxy] to a [Bitmap] with CameraX, which supports `YUV_420_888`, `RGBA_8888` and `JPEG`.
 *
 * @throws VisionCameraException If the image format is not supported.
 */
private fun ImageProxy.toCameraXBitmap(): Bitmap = try {
    toBitmap()
} catch (e: IllegalArgumentException) {
    throw VisionCameraException("Cannot convert an Image with format $format to a Bitmap! (${e.message})", e)
}

/**
 * Rotates this [Bitmap] upright from the given [orientation], and mirrors it if [isMirrored]. This recycles this
 * [Bitmap] if a transformed copy is made.
 *
 * [orientation] is derived from CameraX's `rotationDegrees` (the clockwise rotation that makes the buffer upright),
 * so it is counter-rotated like in `Photo.toBitmap()`.
 */
private fun Bitmap.transformed(orientation: CameraOrientation, isMirrored: Boolean): Bitmap {
    val matrix = Matrix().apply {
        if (orientation != CameraOrientation.UP) postRotate(orientation.counterRotated().degrees.toFloat())
        if (isMirrored) postScale(-1f, 1f)
    }
    // No transforms needed! Just return
    if (matrix.isIdentity) return this
    // We need to transform the Bitmap
    val transformedBitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    if (transformedBitmap !== this) recycle()
    return transformedBitmap
}

/** The bits of a `DEPTH16` sample holding the range, in millimeters. The 3 other bits hold the confidence. */
private const val DEPTH16_RANGE_MASK = 0x1FFF

/** The maximum value of an 8-bit color channel. */
private const val MAX_CHANNEL_VALUE = 255

/**
 * Converts this `DEPTH16` [ImageProxy] to a grey-scale [Bitmap], where black pixels are far away and white pixels are
 * close by, normalized between the closest and farthest valid samples. Samples without a valid range (`0`) are black.
 *
 * CameraX's `toBitmap()` does not support depth formats, so the samples are read from the CPU plane.
 */
private fun ImageProxy.depth16ToBitmap(): Bitmap {
    val plane = planes.singleOrNull() ?: throw VisionCameraException("This Depth does not contain readable Depth Data!")
    val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
    val ranges = IntArray(width * height)
    var minRange = Int.MAX_VALUE
    var maxRange = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val sample = buffer.getShort(y * plane.rowStride + x * plane.pixelStride).toInt()
            val range = sample and DEPTH16_RANGE_MASK
            ranges[y * width + x] = range
            if (range > 0) {
                minRange = minOf(minRange, range)
                maxRange = maxOf(maxRange, range)
            }
        }
    }
    val span = (maxRange - minRange).coerceAtLeast(1)
    val pixels = IntArray(ranges.size) { index ->
        val range = ranges[index]
        val value = if (range == 0) 0 else MAX_CHANNEL_VALUE - (range - minRange) * MAX_CHANNEL_VALUE / span
        Color.rgb(value, value, value)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
