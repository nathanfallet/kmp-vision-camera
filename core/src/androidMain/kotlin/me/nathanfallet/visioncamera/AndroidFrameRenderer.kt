package me.nathanfallet.visioncamera

import android.media.ImageWriter
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage

/**
 * The Android [FrameRenderer], writing [Frame]s into a [Surface] with an [ImageWriter].
 */
internal class AndroidFrameRenderer : FrameRenderer, NativeFrameRenderer {
    @Volatile
    private var imageWriter: ImageWriter? = null

    @OptIn(ExperimentalGetImage::class)
    override fun renderFrame(frame: Frame) {
        val nativeFrame = frame as? NativeFrame
            ?: throw VisionCameraException("Frame is not of type `NativeFrame`!")
        val image = nativeFrame.image.image
            ?: throw VisionCameraException("Image is invalid!")
        imageWriter?.queueInputImage(image)
    }

    @Synchronized
    override fun connectSurface(surface: Surface) {
        disconnectSurface()
        imageWriter = ImageWriter.newInstance(surface, DEFAULT_QUEUE_DEPTH)
    }

    @Synchronized
    override fun disconnectSurface() {
        imageWriter?.close()
        imageWriter = null
    }

    private companion object {
        const val DEFAULT_QUEUE_DEPTH = 3
    }
}
