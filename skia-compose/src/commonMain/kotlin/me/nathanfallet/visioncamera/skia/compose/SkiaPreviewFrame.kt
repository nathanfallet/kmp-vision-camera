package me.nathanfallet.visioncamera.skia.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jetbrains.skia.Image

/**
 * A rendered Frame, ready to be displayed by [SkiaCamera]: created on the Frame's thread, then drawn and closed on
 * the main thread.
 *
 * @param image The rendered CPU image, owned (and closed) by this frame.
 */
internal expect class SkiaPreviewFrame(image: Image) {
    /** The rendered CPU image. */
    val image: Image

    /** Draws the [image] into the whole [DrawScope], scaled to cover it (centered and cropped). */
    fun draw(scope: DrawScope)

    /** Releases the [image] and the platform resources of this frame. */
    fun close()
}

/**
 * The part of an image of the given [imageSize] that covers a destination of the given [destinationSize] once scaled
 * (like React Native Skia's `fit="cover"`): the centered crop with the destination's aspect ratio.
 */
internal fun coverSourceRect(imageSize: Size, destinationSize: Size): Rect {
    if (imageSize.isEmpty() || destinationSize.isEmpty()) return Rect.Zero
    val scale = maxOf(destinationSize.width / imageSize.width, destinationSize.height / imageSize.height)
    val width = destinationSize.width / scale
    val height = destinationSize.height / scale
    val left = (imageSize.width - width) / 2
    val top = (imageSize.height - height) / 2
    return Rect(left, top, left + width, top + height)
}
