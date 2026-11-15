package me.nathanfallet.visioncamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A photo captured in memory by [CameraPhotoOutput.capturePhoto].
 *
 * Save it with [saveToFile] or [saveToTemporaryFile], or read its encoded bytes with [getFileData] (e.g. to decode
 * it into an image to display). Convert it to a displayable [Image] with [toImage], or use the platform-specific
 * conversions: `Photo.toBitmap()` on Android and `Photo.toUIImage()` on iOS.
 *
 * A [Photo] holds native memory: [dispose] it as soon as it is no longer needed. It implements [AutoCloseable], so
 * `use { }` disposes it automatically.
 */
interface Photo : AutoCloseable {
    /** Whether this photo is mirrored along its vertical axis. */
    val isMirrored: Boolean

    /** The [CameraOrientation] this photo was captured in, applied lazily via EXIF or when converting it. */
    val orientation: CameraOrientation

    /** The time this photo was captured at, using the system's host clock, in seconds. */
    val timestamp: Double

    /** Whether this photo is a RAW photo (no processing applied), or a processed one (e.g. JPEG). */
    val isRawPhoto: Boolean

    /** The width of this photo, in pixels. */
    val width: Int

    /** The height of this photo, in pixels. */
    val height: Int

    /** The [PhotoContainerFormat] of this photo. */
    val containerFormat: PhotoContainerFormat

    /** Whether this photo has a native pixel buffer, readable with [getPixelBuffer]. */
    val hasPixelBuffer: Boolean

    /**
     * Gets a copy of this photo's pixels, without EXIF data.
     *
     * @throws VisionCameraException If [hasPixelBuffer] is `false`.
     */
    fun getPixelBuffer(): ByteArray

    /**
     * The [Depth] data captured with this photo, if [CapturePhotoSettings.enableDepthData] was enabled and the device
     * supports it. It is disposed together with this photo.
     */
    val depth: Depth?

    /**
     * The [CameraCalibrationData] captured with this photo, if
     * [CapturePhotoSettings.enableCameraCalibrationDataDelivery] was enabled and the device supports it. iOS only.
     */
    val calibrationData: CameraCalibrationData?

    /**
     * Saves this photo in its [containerFormat] to the given [path], a full filesystem path including the file name
     * and extension. The parent directories must exist, and the file must not.
     */
    suspend fun saveToFile(path: String)

    /**
     * Saves this photo in its [containerFormat] to a temporary file, and returns its filesystem path.
     */
    suspend fun saveToTemporaryFile(): String

    /** Gets the encoded file data of this photo, after processing and including EXIF metadata. */
    suspend fun getFileData(): ByteArray

    /**
     * Converts this photo to a decoded [Image], with its [orientation] and mirroring applied. This runs synchronously,
     * on the calling thread.
     *
     * @throws VisionCameraException If this photo cannot be converted (e.g. it is a RAW photo) or was disposed.
     */
    fun toImage(): Image

    /** Same as [toImage], but runs on a background dispatcher. */
    suspend fun toImageAsync(): Image = withContext(Dispatchers.Default) { toImage() }

    /** Releases the native memory of this photo. It can no longer be used afterwards. */
    fun dispose()

    /** Same as [dispose]. */
    override fun close() = dispose()
}
