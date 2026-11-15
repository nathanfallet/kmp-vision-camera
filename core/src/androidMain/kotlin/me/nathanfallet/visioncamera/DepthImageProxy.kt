package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FlashState
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImmutableImageInfo
import androidx.camera.core.impl.TagBundle

/**
 * An implementation of [ImageProxy] that holds an [Image] in [android.graphics.ImageFormat.DEPTH16].
 */
@SuppressLint("RestrictedApi")
internal class DepthImageProxy(
    private val image: Image,
) : ImageProxy {
    // rotationDegrees and transformMatrix are placeholder values here. CameraX's ImageAnalysis wraps this proxy in a
    // SettableImageProxy with the correct relative rotation and sensor-to-buffer transform before delivering it to
    // the analyzer callback.
    private val imageInfo: ImageInfo = ImmutableImageInfo.create(
        TagBundle.emptyBundle(),
        image.timestamp,
        0,
        Matrix(),
        FlashState.NOT_FIRED,
    )

    override fun close() {
        image.close()
    }

    override val cropRect: Rect
        get() = image.cropRect

    override fun setCropRect(rect: Rect?) {
        image.cropRect = rect
    }

    override val format: Int
        get() = image.format

    override val height: Int
        get() = image.height

    override val width: Int
        get() = image.width

    @ExperimentalGetImage
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> =
        image.planes.map<Image.Plane, ImageProxy.PlaneProxy> { DepthImagePlaneProxy(it) }.toTypedArray()

    override fun getImageInfo(): ImageInfo = imageInfo

    @ExperimentalGetImage
    override fun getImage(): Image = image
}
