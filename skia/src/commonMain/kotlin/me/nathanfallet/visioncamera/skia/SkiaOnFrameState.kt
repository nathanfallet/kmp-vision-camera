package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.Frame
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image

/**
 * Represents the state for rendering a [Frame], see [renderToTexture].
 */
data class SkiaOnFrameState(
    /**
     * The [Frame] wrapped as a drawable GPU texture.
     *
     * The [frameTexture] can either be drawn to the [canvas] directly, in which case it will just be displayed as-is,
     * or drawn with a [org.jetbrains.skia.Paint], in which case it will be used as an input texture for sampling it
     * inside a Skia shader.
     *
     * ```kotlin
     * // Draw as-is:
     * canvas.drawImage(frameTexture, 0f, 0f)
     * // Draw with shader:
     * val paint = Paint().apply { shader = ... }
     * canvas.drawImage(frameTexture, 0f, 0f, paint)
     * ```
     *
     * It is only valid during the draw function: it is released right after it returns.
     */
    val frameTexture: Image,
    /**
     * The GPU canvas to draw the [frameTexture] in. It is already transformed so that drawing the [frameTexture] at
     * `(0, 0)` draws it upright (counter-rotated and un-mirrored).
     *
     * ```kotlin
     * canvas.drawImage(frameTexture, 0f, 0f)
     * ```
     */
    val canvas: Canvas,
)
