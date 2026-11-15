package me.nathanfallet.visioncamera.skia.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * On iOS, Compose itself draws with Skia (skiko): the rendered image is drawn as-is on Compose's canvas.
 */
internal actual class SkiaPreviewFrame actual constructor(actual val image: Image) {
    actual fun draw(scope: DrawScope) {
        val source = coverSourceRect(Size(image.width.toFloat(), image.height.toFloat()), scope.size)
        scope.drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                image,
                Rect.makeLTRB(source.left, source.top, source.right, source.bottom),
                Rect.makeWH(scope.size.width, scope.size.height),
                SamplingMode.LINEAR,
                null,
                true,
            )
        }
    }

    actual fun close() = image.close()
}
