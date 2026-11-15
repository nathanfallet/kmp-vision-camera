package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.DataSpace
import android.hardware.HardwareBuffer
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

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

/** Whether this [HardwareBuffer] can be read on the CPU. */
internal val HardwareBuffer.isCpuReadable: Boolean
    get() {
        val readableUsageFlags = HardwareBuffer.USAGE_CPU_READ_RARELY or HardwareBuffer.USAGE_CPU_READ_OFTEN
        return (usage and readableUsageFlags) != 0L
    }

/**
 * Whether this [ImageProxy]'s pixels can be read on the CPU, aka whether it has CPU-readable GPU-backed pixel data
 * (a `HardwareBuffer`) or CPU-accessible planes.
 */
internal val ImageProxy.hasPixelBuffer: Boolean
    get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hardwareBuffer?.use { hardwareBuffer ->
                if (hardwareBuffer.isCpuReadable) {
                    // We have CPU-readable GPU-backed Pixel Data.
                    return true
                }
            }
        }
        // We have CPU-accessible planes.
        return planes.isNotEmpty()
    }

/**
 * Copies the pixel data of this [ImageProxy] into a new [ByteArray]: a CPU-readable `HardwareBuffer` is read
 * directly, otherwise a single plane is copied as is, and several planes are concatenated.
 *
 * @throws VisionCameraException If this [ImageProxy] does not contain any readable pixel data.
 */
internal fun ImageProxy.getPixelBytes(): ByteArray {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        hardwareBuffer?.use { hardwareBuffer ->
            if (hardwareBuffer.isCpuReadable) {
                // Fast Path: We have a CPU-readable HardwareBuffer.
                return NativeBufferHelper.copyHardwareBufferBytes(hardwareBuffer)
            }
        }
    }
    val planes = planes
    if (planes.isEmpty()) throw VisionCameraException("ImageProxy does not contain any readable Pixel Data!")
    val buffers = planes.map { it.buffer.toReadableByteArray() }
    return if (buffers.size == 1) buffers.single() else buffers.reduce { acc, bytes -> acc + bytes }
}

/** Whether this [ImageProxy] is backed by a `HardwareBuffer`, which can be accessed as a [NativeBuffer]. */
internal val ImageProxy.hasNativeBuffer: Boolean
    get() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }
        hardwareBuffer?.use {
            return true
        }
        return false
    }

/**
 * Gets a [NativeBuffer] to this [ImageProxy]'s `HardwareBuffer`: its [NativeBuffer.pointer] is an `AHardwareBuffer*`
 * acquired with a reference count of +1, until it is [NativeBuffer.release]d.
 *
 * @throws VisionCameraException If this [ImageProxy] is not backed by a `HardwareBuffer` ([hasNativeBuffer]).
 */
internal fun ImageProxy.getNativeBuffer(): NativeBuffer {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        throw VisionCameraException("NativeBuffers are only available on API 28 or higher!")
    }
    val hardwareBuffer = hardwareBuffer
        ?: throw VisionCameraException("Frame does not have a HardwareBuffer!")
    return hardwareBuffer.use {
        HardwareBufferNativeBuffer(NativeBufferHelper.getHardwareBufferPointer(it))
    }
}

/** A [NativeBuffer] holding a +1 reference to an `AHardwareBuffer*`, released once by [release]. */
private class HardwareBufferNativeBuffer(
    override val pointer: Long,
) : NativeBuffer {
    private val wasReleased = AtomicBoolean(false)

    override fun release() {
        if (!wasReleased.compareAndSet(false, true)) {
            throw VisionCameraException("Tried to release NativeBuffer twice!")
        }
        NativeBufferHelper.releaseHardwareBufferPointer(pointer)
    }
}

/** Maps the given [point] with this [Matrix]. */
internal fun Matrix.convertPoint(point: Point): Point {
    val coordinates = floatArrayOf(point.x.toFloat(), point.y.toFloat())
    mapPoints(coordinates)
    return Point(coordinates[0].toDouble(), coordinates[1].toDouble())
}
