package me.nathanfallet.visioncamera

/**
 * A decoded, ready to display image, the equivalent of the `Image` of `react-native-nitro-image` that VisionCamera
 * returns from [Photo.toImage], [CapturePhotoCallbacks.onPreviewImageAvailable], the preview view's `takeSnapshot()`
 * and the [FrameConverter].
 *
 * It wraps the platform's native image, available through platform extensions: `Image.toBitmap()` (an
 * `android.graphics.Bitmap`) on Android, and `Image.toUIImage()` (a `UIImage`) on iOS.
 */
interface Image {
    /** The width of this image, in pixels. */
    val width: Int

    /** The height of this image, in pixels. */
    val height: Int
}
