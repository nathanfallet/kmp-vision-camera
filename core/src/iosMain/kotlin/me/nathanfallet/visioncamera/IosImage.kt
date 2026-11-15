package me.nathanfallet.visioncamera

import kotlinx.cinterop.useContents
import platform.UIKit.UIImage

/**
 * The iOS [Image], wrapping a [UIImage] (V5's `HybridUIImage` of `react-native-nitro-image`).
 */
internal class IosImage(val uiImage: UIImage) : Image {
    override val width: Int get() = uiImage.size.useContents { width * uiImage.scale }.toInt()
    override val height: Int get() = uiImage.size.useContents { height * uiImage.scale }.toInt()

    override fun toString(): String = "Image(${width}x$height)"
}

/**
 * Gets the [UIImage] of this [Image]. Its orientation and mirroring are applied through `UIImage.imageOrientation`.
 *
 * ```kotlin
 * val uiImage = HybridFrameConverter.convertFrameToImage(frame).toUIImage()
 * ```
 *
 * @throws VisionCameraException If this [Image] was not created by VisionCamera.
 */
fun Image.toUIImage(): UIImage {
    val image = this as? IosImage ?: throw VisionCameraException("This Image was not created by VisionCamera!")
    return image.uiImage
}

/**
 * Wraps this [UIImage] in an [Image], without copying it.
 */
fun UIImage.toImage(): Image = IosImage(this)
