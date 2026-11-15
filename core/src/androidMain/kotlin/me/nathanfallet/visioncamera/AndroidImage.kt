package me.nathanfallet.visioncamera

import android.graphics.Bitmap

/**
 * The Android [Image], wrapping a [Bitmap] (V5's `HybridImage` of `react-native-nitro-image`).
 */
internal class AndroidImage(val bitmap: Bitmap) : Image {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun toString(): String = "Image(${width}x$height)"
}

/**
 * Gets the Android [Bitmap] of this [Image].
 *
 * ```kotlin
 * val bitmap = previewViewRef.takeSnapshot().toBitmap()
 * ```
 *
 * @throws VisionCameraException If this [Image] was not created by VisionCamera.
 */
fun Image.toBitmap(): Bitmap {
    val image = this as? AndroidImage ?: throw VisionCameraException("This Image was not created by VisionCamera!")
    return image.bitmap
}

/**
 * Wraps this [Bitmap] in an [Image], without copying it.
 */
fun Bitmap.toImage(): Image = AndroidImage(this)
