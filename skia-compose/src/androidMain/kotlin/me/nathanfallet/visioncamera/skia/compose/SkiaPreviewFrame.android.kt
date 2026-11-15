package me.nathanfallet.visioncamera.skia.compose

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.BufferUtil
import kotlin.math.roundToInt

/**
 * On Android, Compose draws with the platform's Canvas: the rendered image's pixels are copied into an Android
 * [Bitmap] (recycled across frames), drawn by Compose.
 */
internal actual class SkiaPreviewFrame actual constructor(actual val image: Image) {
    private val bitmap: Bitmap = BitmapPool.acquire(image.width, image.height)
    private val imageBitmap: ImageBitmap

    init {
        val pixmap = checkNotNull(image.peekPixels()) { "The rendered image is not a CPU image!" }
        try {
            check(pixmap.info.colorInfo.colorType == ColorType.RGBA_8888 && pixmap.rowBytes == bitmap.rowBytes) {
                "Unexpected rendered image layout: ${pixmap.info.colorInfo.colorType}, ${pixmap.rowBytes} bytes/row"
            }
            bitmap.copyPixelsFromBuffer(BufferUtil.getByteBufferFromPointer(pixmap.addr, pixmap.computeByteSize()))
        } finally {
            pixmap.close()
        }
        imageBitmap = bitmap.asImageBitmap()
    }

    actual fun draw(scope: DrawScope) {
        val source = coverSourceRect(Size(image.width.toFloat(), image.height.toFloat()), scope.size)
        scope.drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(source.left.roundToInt(), source.top.roundToInt()),
            srcSize = IntSize(source.width.roundToInt(), source.height.roundToInt()),
            dstSize = IntSize(scope.size.width.roundToInt(), scope.size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }

    actual fun close() {
        image.close()
        BitmapPool.release(bitmap)
    }
}

/**
 * Recycles the [Bitmap]s of [SkiaPreviewFrame]s. A released bitmap is only reused after the next one is released, so
 * that the render thread is done drawing it.
 */
private object BitmapPool {
    private const val MAXIMUM_SIZE = 2
    private val bitmaps = ArrayDeque<Bitmap>()
    private var retired: Bitmap? = null

    @Synchronized
    fun acquire(width: Int, height: Int): Bitmap {
        val index = bitmaps.indexOfFirst { it.width == width && it.height == height }
        return if (index >= 0) bitmaps.removeAt(index) else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun release(bitmap: Bitmap) {
        retired?.let { previous ->
            bitmaps.addLast(previous)
            if (bitmaps.size > MAXIMUM_SIZE) bitmaps.removeFirst()
        }
        retired = bitmap
    }
}
