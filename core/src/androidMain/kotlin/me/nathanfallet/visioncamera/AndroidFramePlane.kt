package me.nathanfallet.visioncamera

import androidx.camera.core.ImageProxy

/**
 * The Android [FramePlane], wrapping a CameraX [ImageProxy.PlaneProxy].
 */
internal class AndroidFramePlane(
    private val plane: ImageProxy.PlaneProxy,
) : FramePlane {
    override val width: Int
        get() = if (isValid) plane.rowStride / plane.pixelStride else 0

    override val height: Int
        get() = if (isValid) plane.buffer.capacity() / plane.rowStride else 0

    override val bytesPerRow: Int
        get() = plane.rowStride

    override val isValid: Boolean
        get() = try {
            plane.buffer
            true
        } catch (_: IllegalStateException) {
            // Accessing the buffer of a plane of a closed Image throws.
            false
        }

    override fun getPixelBuffer(): ByteArray {
        if (!isValid) throw VisionCameraException("This FramePlane is not valid anymore!")
        return plane.buffer.toReadableByteArray()
    }
}
