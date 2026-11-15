package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.DataSpace
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy

private val ImageProxy.pixelRange: PixelRange
    @SuppressLint("WrongConstant")
    @OptIn(ExperimentalGetImage::class)
    get() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PixelRange.UNKNOWN
        val dataSpace = image?.dataSpace ?: return PixelRange.UNKNOWN
        return when (DataSpace.getRange(dataSpace)) {
            DataSpace.RANGE_LIMITED -> PixelRange.VIDEO
            DataSpace.RANGE_FULL -> PixelRange.FULL
            else -> PixelRange.UNKNOWN
        }
    }

/** The [PixelFormat] of this [ImageProxy]. */
internal val ImageProxy.pixelFormat: PixelFormat
    get() = pixelFormatFromImageFormat(format, pixelRange)

/** The [DepthPixelFormat] of this [ImageProxy]. */
internal val ImageProxy.depthPixelFormat: DepthPixelFormat
    get() = when (format) {
        ImageFormat.DEPTH16 -> DepthPixelFormat.DEPTH_16_BIT
        ImageFormat.DEPTH_POINT_CLOUD -> DepthPixelFormat.DEPTH_POINT_CLOUD_32_BIT
        else -> DepthPixelFormat.UNKNOWN
    }

/**
 * Whether this [ImageProxy]'s pixels can be read on the CPU, aka whether it has CPU-accessible planes.
 *
 * Unlike VisionCamera, CPU-readable `HardwareBuffer`s without planes are not supported, since reading them requires
 * the NDK.
 */
internal val ImageProxy.hasPixelBuffer: Boolean
    get() = planes.isNotEmpty()

/**
 * Copies the pixel data of this [ImageProxy] into a new [ByteArray]: a single plane is copied as is, several planes
 * are concatenated.
 *
 * @throws VisionCameraException If this [ImageProxy] does not contain any readable pixel data.
 */
internal fun ImageProxy.getPixelBytes(): ByteArray {
    val planes = planes
    if (planes.isEmpty()) throw VisionCameraException("ImageProxy does not contain any readable Pixel Data!")
    val buffers = planes.map { it.buffer.toReadableByteArray() }
    return if (buffers.size == 1) buffers.single() else buffers.reduce { acc, bytes -> acc + bytes }
}

/** Maps the given [point] with this [Matrix]. */
internal fun Matrix.convertPoint(point: Point): Point {
    val coordinates = floatArrayOf(point.x.toFloat(), point.y.toFloat())
    mapPoints(coordinates)
    return Point(coordinates[0].toDouble(), coordinates[1].toDouble())
}
