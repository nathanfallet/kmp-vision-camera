package me.nathanfallet.visioncamera

import androidx.camera.core.ImageProxy

/**
 * A [Frame] or [Depth] created by VisionCamera on Android, backed by a CameraX [ImageProxy].
 *
 * Cast a [Frame] to a [NativeFrame] to access its [image], e.g. for ML Kit:
 * ```kotlin
 * val image = (frame as NativeFrame).image.image
 * ```
 */
interface NativeFrame {
    /** The CameraX [ImageProxy] backing this Frame. It is closed when the Frame is disposed. */
    val image: ImageProxy
}
