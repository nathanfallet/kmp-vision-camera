package me.nathanfallet.visioncamera

/**
 * Configuration options of a [CameraPhotoOutput], see [CameraFactory.createPhotoOutput].
 */
data class PhotoOutputOptions(
    /**
     * The target photo resolution. The [CameraSession] negotiates it with the other outputs and constraints, so it
     * may not be met exactly; its aspect ratio is prioritized over its pixel count.
     */
    val targetResolution: Size = CommonResolutions.UHD_4_3,
    /** The [TargetPhotoContainerFormat] to shoot photos in. */
    val containerFormat: TargetPhotoContainerFormat = TargetPhotoContainerFormat.NATIVE,
    /**
     * The compression quality of processed formats, from `0.0` (strongest compression) to `1.0` (highest quality).
     * RAW formats ignore it.
     */
    val quality: Double = 0.9,
    /**
     * The balance between speed and quality of the capture pipeline. [QualityPrioritization.SPEED] requires
     * [CameraDevice.supportsSpeedQualityPrioritization].
     */
    val qualityPrioritization: QualityPrioritization = QualityPrioritization.BALANCED,
    /**
     * When set, a ready to display [Image] of about this [Size] is delivered to
     * [CapturePhotoCallbacks.onPreviewImageAvailable] just before the resulting [Photo] is available.
     *
     * Preview image delivery requires additional processing, so it is disabled by default (`null`). Keep this size as
     * small as possible to avoid unnecessary overhead. Requires [CameraDevice.supportsPreviewImage].
     */
    val previewImageTargetSize: Size? = null,
)

/**
 * Callbacks of a [CameraPhotoOutput.capturePhoto] call.
 */
data class CapturePhotoCallbacks(
    /** Called when the pipeline starts the capture. */
    val onWillBeginCapture: (() -> Unit)? = null,
    /** Called right before the pipeline captures the photo. */
    val onWillCapturePhoto: (() -> Unit)? = null,
    /** Called right after the pipeline captured the photo. */
    val onDidCapturePhoto: (() -> Unit)? = null,
    /**
     * Called with an [Image] suitable for preview or thumbnail updates, before the resulting [Photo] is available.
     *
     * Only called if [PhotoOutputOptions.previewImageTargetSize] is set and the [CameraDevice] supports preview images
     * ([CameraDevice.supportsPreviewImage]). It may be called on a background thread.
     */
    val onPreviewImageAvailable: ((previewImage: Image) -> Unit)? = null,
)

/**
 * The flash mode of a photo capture, see [CapturePhotoSettings.flashMode].
 */
enum class FlashMode {
    /** The flash is not used. */
    OFF,

    /** The flash is always used. Requires [CameraDevice.hasFlash]. */
    ON,

    /** The flash is used when the scene is too dark. */
    AUTO,
}

/**
 * Settings of a single [CameraPhotoOutput.capturePhoto] call.
 */
data class CapturePhotoSettings(
    /** The [FlashMode] of this capture. [FlashMode.ON] requires [CameraDevice.hasFlash]. */
    val flashMode: FlashMode = FlashMode.OFF,
    /**
     * Whether to play the system shutter sound when the photo is captured. The OS may override it, e.g. with
     * regional sound-suppression settings.
     */
    val enableShutterSound: Boolean = true,
    /**
     * Whether to deliver depth data with the photo, readable with [Photo.depth]. Requires
     * [CameraPhotoOutput.supportsDepthDataDelivery].
     */
    val enableDepthData: Boolean = false,
    /** Whether red-eye reduction is applied on flash captures. */
    val enableRedEyeReduction: Boolean = true,
    /**
     * Whether to deliver camera calibration data with the photo, readable with [Photo.calibrationData]. Requires
     * [CameraPhotoOutput.supportsCameraCalibrationDataDelivery].
     */
    val enableCameraCalibrationDataDelivery: Boolean = false,
    /**
     * Whether the pipeline corrects lens distortion. Captured photos may then have a narrower field of view than the
     * preview.
     */
    val enableDistortionCorrection: Boolean = false,
    /**
     * Whether a device with several physical devices may fuse their images to improve quality. Disabled for RAW
     * captures.
     */
    val enableVirtualDeviceFusion: Boolean = true,
    /** The [Location] to embed in the photo's EXIF metadata. */
    val location: Location? = null,
)

/**
 * A photo captured by [CameraPhotoOutput.capturePhotoToFile] and written to a file.
 */
data class PhotoFile(
    /** The filesystem path of the file (not a `file://` URL). */
    val filePath: String,
    /** The width of the photo, in pixels. */
    val width: Int,
    /** The height of the photo, in pixels. */
    val height: Int,
    /** The [CameraOrientation] the photo was captured in, applied lazily through the file's EXIF metadata. */
    val orientation: CameraOrientation,
    /** Whether the photo is mirrored along its vertical axis. */
    val isMirrored: Boolean,
    /** The time the photo was captured at, using the system's host clock, in seconds. */
    val timestamp: Double,
    /** Whether the photo is a RAW photo. */
    val isRawPhoto: Boolean,
    /** The [PhotoContainerFormat] of the photo. */
    val containerFormat: PhotoContainerFormat,
)

/**
 * A [CameraOutput] capturing [Photo]s, in memory ([capturePhoto]) or directly to a file ([capturePhotoToFile]).
 *
 * ```kotlin
 * val photoOutput = VisionCamera.createPhotoOutput(PhotoOutputOptions(targetResolution = CommonResolutions.UHD_4_3))
 * // ... connect it to a CameraSession, then:
 * photoOutput.capturePhoto().use { photo ->
 *     photo.saveToFile(path)
 * }
 * ```
 */
interface CameraPhotoOutput : CameraOutput {
    /** Whether this output can deliver depth data with photos ([CapturePhotoSettings.enableDepthData]). */
    val supportsDepthDataDelivery: Boolean

    /**
     * Whether this output can deliver camera calibration data with photos
     * ([CapturePhotoSettings.enableCameraCalibrationDataDelivery]).
     */
    val supportsCameraCalibrationDataDelivery: Boolean

    /**
     * Captures a [Photo] in memory with the given [settings].
     *
     * The returned [Photo] holds native memory: [Photo.dispose] it (or use it in a `use` block) once it is no
     * longer needed. On Android, prefer [capturePhotoToFile] for formats other than JPEG, as CameraX does not
     * properly support in-memory RAW photos yet.
     */
    suspend fun capturePhoto(
        settings: CapturePhotoSettings = CapturePhotoSettings(),
        callbacks: CapturePhotoCallbacks = CapturePhotoCallbacks(),
    ): Photo

    /**
     * Captures a photo with the given [settings] and writes it to a temporary file.
     */
    suspend fun capturePhotoToFile(
        settings: CapturePhotoSettings = CapturePhotoSettings(),
        callbacks: CapturePhotoCallbacks = CapturePhotoCallbacks(),
    ): PhotoFile

    /**
     * Prepares the pipeline for the given [settings], so that later [capturePhoto] calls with any of them start
     * faster. Optional; a no-op on Android.
     */
    suspend fun prepareSettings(settings: List<CapturePhotoSettings>)
}
