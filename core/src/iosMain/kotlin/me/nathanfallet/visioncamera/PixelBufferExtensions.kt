package me.nathanfallet.visioncamera

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetPlaneCount
import platform.CoreVideo.CVPixelBufferIsPlanar
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.posix.memcpy
import kotlin.concurrent.AtomicReference

// Port of V5's ArrayBuffer+fromPixelBuffer.swift and CVPixelBuffer+asNativeBuffer.swift.

/**
 * Copies the pixels of this **non-planar** pixel buffer into a new [ByteArray] (V5's `ArrayBuffer.fromPixelBuffer`,
 * which wraps the memory without copying instead). The buffer must already be locked.
 *
 * @throws VisionCameraException If the buffer's memory cannot be accessed.
 */
internal fun CVPixelBufferRef.copyToByteArray(): ByteArray {
    val baseAddress = CVPixelBufferGetBaseAddress(this)
        ?: throw VisionCameraException("Failed to get CVPixelBuffer's base address!")
    return copyBytes(baseAddress, CVPixelBufferGetDataSize(this))
}

/**
 * Copies the plane at the given [planeIndex] of this **planar** pixel buffer into a new [ByteArray]. The buffer must
 * already be locked.
 *
 * @throws VisionCameraException If the buffer is not planar, the plane does not exist or cannot be accessed.
 */
@Suppress("ThrowsCount")
internal fun CVPixelBufferRef.copyPlaneToByteArray(planeIndex: Int): ByteArray {
    if (!CVPixelBufferIsPlanar(this)) {
        throw VisionCameraException(
            "Cannot convert plane #$planeIndex of a non-planar CVPixelBuffer to an ArrayBuffer! " +
                "Use the non-planar variant.",
        )
    }
    val planeCount = CVPixelBufferGetPlaneCount(this).toInt()
    if (planeIndex !in 0 until planeCount) {
        throw VisionCameraException(
            "Cannot convert plane #$planeIndex of a CVPixelBuffer with only $planeCount planes to an ArrayBuffer - " +
                "it's out of range!",
        )
    }
    val index = planeIndex.toULong()
    val baseAddress = CVPixelBufferGetBaseAddressOfPlane(this, index)
        ?: throw VisionCameraException("Failed to get CVPixelBuffer's base address!")
    val size = CVPixelBufferGetBytesPerRowOfPlane(this, index) * CVPixelBufferGetHeightOfPlane(this, index)
    return copyBytes(baseAddress, size)
}

private fun copyBytes(source: COpaquePointer, size: ULong): ByteArray {
    val bytes = ByteArray(size.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), source, size) }
    }
    return bytes
}

/**
 * Wraps this pixel buffer in a [NativeBuffer], retaining it (+1) until [NativeBuffer.release] is called.
 */
internal fun CVPixelBufferRef.asNativeBuffer(): NativeBuffer = PixelBufferNativeBuffer(this)

/** A [NativeBuffer] holding a +1 reference to a `CVPixelBufferRef`. */
private class PixelBufferNativeBuffer(pixelBuffer: CVPixelBufferRef) : NativeBuffer {
    private val retained = AtomicReference<CVPixelBufferRef?>(CVPixelBufferRetain(pixelBuffer)) // +1 retain

    override val pointer: Long = pixelBuffer.toLong()

    override fun release() {
        val pixelBuffer = retained.getAndSet(null)
        if (pixelBuffer == null) {
            Logger.error("Tried to release NativeBuffer twice!")
            return
        }
        CVPixelBufferRelease(pixelBuffer) // -1 retain
    }
}
