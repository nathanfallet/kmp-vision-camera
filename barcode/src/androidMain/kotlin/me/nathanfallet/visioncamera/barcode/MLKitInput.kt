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
 * An ML Kit [InputImage] to scan, and the [BufferCoordinateSpace] its results are converted to.
 */
internal class MLKitInput(
    /** The image ML Kit scans. */
    val image: InputImage,
    /** The coordinate space of the scanned input, which ML Kit's coordinates are converted to. */
    val space: BufferCoordinateSpace,
)

/**
 * Wraps this [Frame] in an ML Kit [InputImage] (V5's `ML+HybridFrameSpec.kt`).
 *
 * The [InputImage] is given the Frame's `rotationDegrees` (ML Kit detects best upright), and the [MLKitInput.space]
 * converts ML Kit's upright coordinates back to the Frame's raw buffer, the coordinate system that
 * [Frame.convertFramePointToCameraPoint] expects.
 *
 * @throws VisionCameraException If this [Frame] was not created by VisionCamera, or has no underlying image.
 */
internal fun Frame.toMLKitInput(): MLKitInput {
    val frame = this as? NativeFrame ?: throw VisionCameraException("Frame is not of type `NativeFrame`!")
    return frame.image.toMLKitInput()
}

/**
 * Wraps this [ImageProxy] in an ML Kit [InputImage], with its `rotationDegrees`, along with the
 * [BufferCoordinateSpace] of its raw buffer.
 *
 * @throws VisionCameraException If this [ImageProxy] has no underlying image.
 */
@OptIn(ExperimentalGetImage::class)
internal fun ImageProxy.toMLKitInput(): MLKitInput {
    val rotationDegrees = imageInfo.rotationDegrees
    val inputImage = if (format == PixelFormat.RGBA_8888) {
        // ML Kit cannot wrap an RGBA android.media.Image directly.
        InputImage.fromBitmap(toBitmap(), rotationDegrees)
    } else {
        // Keep compatible formats such as YUV_420_888 on the zero-copy path.
        val mediaImage = image ?: throw VisionCameraException("Frame does not have an underlying `Image`!")
        InputImage.fromMediaImage(mediaImage, rotationDegrees)
    }
    return MLKitInput(inputImage, BufferCoordinateSpace(width, height, rotationDegrees))
}

/**
 * Wraps this [Image]'s Bitmap in an ML Kit [InputImage] (V5's `ML+HybridImageSpec.kt`), copying it first if it is
 * a hardware Bitmap, whose pixels the CPU cannot read. ML Kit is given no rotation, so the coordinates stay in the
 * [Image]'s coordinate system.
 *
 * @throws VisionCameraException If this [Image] was not created by VisionCamera.
 */
internal fun Image.toMLKitInput(): MLKitInput {
    val bitmap = toBitmap()
    val cpuBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    val space = BufferCoordinateSpace(cpuBitmap.width, cpuBitmap.height, rotationDegrees = 0)
    return MLKitInput(InputImage.fromBitmap(cpuBitmap, 0), space)
}
