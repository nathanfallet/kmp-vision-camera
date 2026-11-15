package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.NativeBuffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Renders the given [frame] to a Skia [Image] and returns it.
 *
 * The [frame] is wrapped as a GPU texture without any copy (through its [NativeBuffer]), and [onDraw] draws it (and
 * anything else) into an offscreen GPU canvas the size of the upright [frame], transformed to cancel the [frame]'s
 * [Frame.orientation] and [Frame.isMirrored]: drawing the texture at `(0, 0)` draws it upright.
 *
 * ```kotlin
 * val image = renderToTexture(frame) { (frameTexture, canvas) ->
 *     canvas.drawImage(frameTexture, 0f, 0f)
 * }
 * ```
 *
 * The returned image is a GPU image of the calling thread's GPU context: use it on this thread, or copy it to the CPU
 * with [makeNonTextureImage] (on this thread) to use it anywhere. Close it when you are done with it.
 *
 * @param frame The [Frame] to render. It will be converted to a texture. It must have a native buffer (see
 * [Frame.hasNativeBuffer]).
 * @param onDraw A provided draw function to perform the rendering.
 */
fun renderToTexture(frame: Frame, onDraw: (state: SkiaOnFrameState) -> Unit): Image {
    // 1. Compute target dimensions
    val isLandscape = frame.orientation == CameraOrientation.LEFT || frame.orientation == CameraOrientation.RIGHT
    val outWidth = if (isLandscape) frame.height else frame.width
    val outHeight = if (isLandscape) frame.width else frame.height

    // 2. Get drawable offscreen surface (cached per Thread & Size)
    return surfacesCache.withSurface(outWidth, outHeight) { context, surface ->
        context.makeCurrent()
        var nativeBuffer: NativeBuffer? = null
        var frameTexture: FrameTexture? = null
        try {
            // 3. Make a Texture from the Frame (via NativeBuffer)
            nativeBuffer = frame.getNativeBuffer()
            frameTexture = context.makeFrameTexture(nativeBuffer)
            // 4. Prepare a Canvas for drawing
            val canvas = surface.canvas

            // 5. Apply any transforms to cancel Frame orientation/mirrored
            val saveCount = canvas.save()
            try {
                val rotation = orientationToDegrees(frame.orientation)
                val counterRotation = normalizeRotationDegrees(-rotation)
                // 5.1. Move it to the center so we rotate around center origin
                canvas.translate(outWidth / 2f, outHeight / 2f)
                // 5.2. Mirror if needed.
                // Note: Skia concatenates transforms, so call order is reversed at draw-time.
                // Placing scale() before rotate() makes the rendered result rotate first, then mirror.
                if (frame.isMirrored) {
                    // Mirror alongside the vertical axis (horizontal flip)
                    canvas.scale(-1f, 1f)
                }
                // 5.3. Rotate the Frame
                canvas.rotate(counterRotation.toFloat())
                // 5.4. Counter back the center origin transforms
                if (isLandscape) {
                    canvas.translate(-(outHeight / 2f), -(outWidth / 2f))
                } else {
                    canvas.translate(-(outWidth / 2f), -(outHeight / 2f))
                }

                // 6. Draw!
                onDraw(SkiaOnFrameState(frameTexture.image, canvas))
            } finally {
                // 7. Restore canvas transforms
                canvas.restoreToCount(saveCount)
            }

            // 8. Snapshot the Surface contents to get it into an Image
            surface.makeImageSnapshot()
        } finally {
            // 9. Dispose everything that we allocated
            frameTexture?.release()
            nativeBuffer?.release()
        }
    }
}

/**
 * Copies this image to the CPU (React Native Skia's `SkImage.makeNonTextureImage()`, which skiko lacks): the result
 * is a raster image that can be used on any thread, e.g. to display it in the UI or to encode it.
 *
 * ```kotlin
 * val snapshot = renderToTexture(frame) { (frameTexture, canvas) -> canvas.drawImage(frameTexture, 0f, 0f) }
 * val cpuImage = snapshot.makeNonTextureImage()
 * snapshot.close()
 * ```
 *
 * A GPU image returned by [renderToTexture] must be copied on the thread that rendered it.
 *
 * @throws IllegalStateException If the pixels could not be read, e.g. because a GPU image is copied on another
 * thread.
 */
fun Image.makeNonTextureImage(): Image {
    val bitmap = Bitmap()
    try {
        check(bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))) {
            "Failed to allocate a ${width}x$height bitmap!"
        }
        val context = surfacesCache.currentContext()
        val isRead = if (context != null) {
            context.makeCurrent()
            readPixels(context.directContext, bitmap, 0, 0)
        } else {
            readPixels(bitmap, 0, 0)
        }
        check(isRead) {
            "Failed to copy rendered GPU contents to CPU Image! GPU images must be copied on the thread that " +
                "rendered them."
        }
        bitmap.setImmutable()
        return Image.makeFromBitmap(bitmap)
    } finally {
        bitmap.close()
    }
}
