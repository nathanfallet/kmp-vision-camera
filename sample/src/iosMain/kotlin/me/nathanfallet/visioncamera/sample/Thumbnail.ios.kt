package me.nathanfallet.visioncamera.sample

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.nathanfallet.visioncamera.Photo
import me.nathanfallet.visioncamera.toUIImage
import org.jetbrains.skia.Image
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun Photo.toThumbnail(): ImageBitmap {
    // Draw the UIImage once to bake its orientation into the pixels, then decode the PNG with Skia.
    val image: UIImage = toUIImage()
    val png = UIGraphicsImageRenderer(size = image.size).PNGDataWithActions { _ ->
        image.drawAtPoint(platform.CoreGraphics.CGPointMake(0.0, 0.0))
    }
    val bytes = ByteArray(png.length.toInt())
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), png.bytes, png.length) }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
