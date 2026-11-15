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
     * or sampled inside a Skia shader: draw a rect covering the [frameTexture] with a [org.jetbrains.skia.Paint] whose
     * shader samples it (Skia ignores the shader of the paint given to `drawImage`).
     *
     * ```kotlin
     * // Draw as-is:
     * canvas.drawImage(frameTexture, 0f, 0f)
     * // Draw with shader:
     * val paint = Paint().apply { shader = ... }
     * canvas.drawRect(Rect.makeWH(frameTexture.width.toFloat(), frameTexture.height.toFloat()), paint)
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
