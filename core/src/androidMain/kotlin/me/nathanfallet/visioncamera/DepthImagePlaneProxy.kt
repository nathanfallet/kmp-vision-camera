package me.nathanfallet.visioncamera

import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * An implementation of [ImageProxy.PlaneProxy] that holds an [Image.Plane].
 */
@ExperimentalGetImage
internal class DepthImagePlaneProxy(
    private val plane: Image.Plane,
) : ImageProxy.PlaneProxy {
    override val rowStride: Int
        get() = plane.rowStride

    override val pixelStride: Int
        get() = plane.pixelStride

    override val buffer: ByteBuffer
        get() = plane.buffer
}
