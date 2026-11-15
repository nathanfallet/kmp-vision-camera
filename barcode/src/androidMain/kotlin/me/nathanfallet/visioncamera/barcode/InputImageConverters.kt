package me.nathanfallet.visioncamera.barcode

import android.graphics.Bitmap
import android.graphics.PixelFormat
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image
import me.nathanfallet.visioncamera.NativeFrame
import me.nathanfallet.visioncamera.VisionCameraException
import me.nathanfallet.visioncamera.toBitmap

/**
 * Wraps this [Frame] in an ML Kit [InputImage] (V5's `ML+HybridFrameSpec.kt`).
 *
 * The [InputImage] is given the Frame's `rotationDegrees`, so ML Kit reports the [Barcode] coordinates relative to
 * the upright image, like V5.
 *
 * @throws VisionCameraException If this [Frame] was not created by VisionCamera, or has no underlying image.
 */
internal fun Frame.toInputImage(): InputImage {
    val frame = this as? NativeFrame ?: throw VisionCameraException("Frame is not of type `NativeFrame`!")
    return frame.image.toInputImage()
}

/**
 * Wraps this [ImageProxy] in an ML Kit [InputImage], with its `rotationDegrees`.
 *
 * @throws VisionCameraException If this [ImageProxy] has no underlying image.
 */
@OptIn(ExperimentalGetImage::class)
internal fun ImageProxy.toInputImage(): InputImage {
    val rotationDegrees = imageInfo.rotationDegrees
    return if (format == PixelFormat.RGBA_8888) {
        // ML Kit cannot wrap an RGBA android.media.Image directly.
        InputImage.fromBitmap(toBitmap(), rotationDegrees)
    } else {
        // Keep compatible formats such as YUV_420_888 on the zero-copy path.
        val mediaImage = image ?: throw VisionCameraException("Frame does not have an underlying `Image`!")
        InputImage.fromMediaImage(mediaImage, rotationDegrees)
    }
}

/**
 * Wraps this [Image]'s Bitmap in an ML Kit [InputImage] (V5's `ML+HybridImageSpec.kt`), copying it first if it is
 * a hardware Bitmap, whose pixels the CPU cannot read.
 *
 * @throws VisionCameraException If this [Image] was not created by VisionCamera.
 */
internal fun Image.toInputImage(): InputImage {
    val bitmap = toBitmap()
    val cpuBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    return InputImage.fromBitmap(cpuBitmap, 0)
}
