package me.nathanfallet.visioncamera

import platform.UIKit.UIImage

/**
 * Converts this [Photo] to a [UIImage], applying its [Photo.orientation] and [Photo.isMirrored] through the image's
 * `imageOrientation`. Same as [Photo.toImage] followed by `Image.toUIImage()`.
 *
 * ```kotlin
 * val image = photoOutput.capturePhoto().use { it.toUIImage() }
 * ```
 *
 * @throws VisionCameraException If this photo was not captured by VisionCamera, was disposed, or cannot be converted
 * (e.g. it is a RAW photo).
 */
fun Photo.toUIImage(): UIImage {
    val photo = this as? IosPhoto ?: throw VisionCameraException("Photo \"$this\" was not captured by VisionCamera!")
    return photo.toUIImage()
}
