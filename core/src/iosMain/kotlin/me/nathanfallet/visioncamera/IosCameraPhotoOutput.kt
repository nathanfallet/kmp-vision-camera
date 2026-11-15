package me.nathanfallet.visioncamera

import kotlinx.cinterop.cValue
import kotlinx.coroutines.CompletableDeferred
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.CMVideoDimensionsValue
import platform.CoreMedia.CMVideoDimensions
import platform.Foundation.NSValue

/**
 * The iOS [CameraPhotoOutput], backed by an [AVCapturePhotoOutput] (port of V5's `HybridCameraPhotoOutput.swift`).
 */
internal class IosCameraPhotoOutput(private val options: PhotoOutputOptions) : CameraPhotoOutput, NativeCameraOutput {
    // TODO(phase 2): depth and calibration data delivery.
    override val supportsDepthDataDelivery: Boolean = false

    override val supportsCameraCalibrationDataDelivery: Boolean = false

    override val mediaType: MediaType = MediaType.VIDEO
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = false
    override val output: AVCapturePhotoOutput = AVCapturePhotoOutput()

    private val videoConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeVideo)

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            val connection = videoConnection ?: return
            runCatching { connection.setOrientation(value) }
        }

    override val currentResolution: Size?
        get() {
            // Reads back what `configure(config)` pinned via `output.maxPhotoDimensions`.
            if (isIOSAtLeast(major = 16)) return output.maxPhotoDimensions.toSize()
            val device = output.connections.filterIsInstance<AVCaptureConnection>().firstOrNull()?.deviceInput?.device
            @Suppress("DEPRECATION")
            return device?.activeFormat?.highResolutionStillImageDimensions?.toSize()
        }

    override val streamType: StreamType = StreamType.PHOTO

    override val targetResolution: ResolutionRule
        get() = ResolutionRule.ClosestTo(options.targetResolution)

    init {
        output.maxPhotoQualityPrioritization = options.qualityPrioritization.toAVCapturePhotoQualityPrioritization()
        if (options.containerFormat == TargetPhotoContainerFormat.DNG) {
            // If we capture RAW photos, try using Apple ProRAW. If not, Bayer14 RAW will be used.
            output.appleProRAWEnabled = output.appleProRAWSupported
        }
        if (isIOSAtLeast(major = 26) && output.cameraSensorOrientationCompensationSupported) {
            // Don't rotate Photo buffers - we handle orientation later on in file capture or toUIImage().
            output.cameraSensorOrientationCompensationEnabled = false
        }
        // Prepare the default Photo Settings to make the pipeline ready - some things (like flashMode) might change on
        // a per-capture basis, but containerFormat and preview size is already known and can be prepared already.
        runCatching { prepareDefaultPhotoSettings() }
    }

    override fun configure(config: OutputConfiguration) {
        val connection = videoConnection ?: return
        runCatching { connection.setOrientation(outputOrientation) }
        runCatching { connection.setMirrorMode(config.mirrorMode) }

        if (isIOSAtLeast(major = 16)) {
            // Configure PhotoOutput to the currently selected Format's max photo size
            val format = connection.deviceInput?.device?.activeFormat ?: return
            val nearestPhotoDimension = format.supportedMaxPhotoDimensions
                .filterIsInstance<NSValue>()
                .map { it.CMVideoDimensionsValue.toSize() }
                .nearest(options.targetResolution)
            if (nearestPhotoDimension != null && output.maxPhotoDimensions.toSize() != nearestPhotoDimension) {
                // Target max photo dimensions have changed, re-configure
                output.maxPhotoDimensions = cValue<CMVideoDimensions> {
                    width = nearestPhotoDimension.width
                    height = nearestPhotoDimension.height
                }
                runCatching { prepareDefaultPhotoSettings() }
            }
        }
    }

    private fun prepareDefaultPhotoSettings() {
        // Create default capture settings, and prepare the output for them so coming up captures are faster
        val avSettings = CapturePhotoSettings().toAVCapturePhotoSettings(output, options)
        output.setPreparedPhotoSettingsArray(listOf(avSettings), completionHandler = null)
    }

    override suspend fun capturePhoto(settings: CapturePhotoSettings, callbacks: CapturePhotoCallbacks): Photo {
        if (videoConnection == null) {
            throw VisionCameraException("PhotoOutput is not yet connected to the CameraSession!")
        }
        // 1. Prepare Photo settings
        val captureSettings = settings.toAVCapturePhotoSettings(output, options)
        // 2. Prepare delegate that will resolve/reject
        val result = CompletableDeferred<Photo>()
        val resultingFormat = photoContainerFormatOf(options.containerFormat)
        val delegate = CapturePhotoDelegate(
            onCaptured = { photo, metadata ->
                // Wrap the `AVCapturePhoto` in an `IosPhoto` and resolve
                result.complete(IosPhoto(photo = photo, metadata = metadata, containerFormat = resultingFormat))
            },
            onError = { error -> result.completeExceptionally(error) },
            callbacks = callbacks,
        )
        // 3. Perform capture
        output.capturePhotoWithSettings(captureSettings, delegate)
        return result.await()
    }

    override suspend fun capturePhotoToFile(
        settings: CapturePhotoSettings,
        callbacks: CapturePhotoCallbacks,
    ): PhotoFile = capturePhoto(settings, callbacks).use { photo ->
        val filePath = photo.saveToTemporaryFile()
        PhotoFile(
            filePath = filePath,
            width = photo.width,
            height = photo.height,
            orientation = photo.orientation,
            isMirrored = photo.isMirrored,
            timestamp = photo.timestamp,
            isRawPhoto = photo.isRawPhoto,
            containerFormat = photo.containerFormat,
        )
    }

    override suspend fun prepareSettings(settings: List<CapturePhotoSettings>) {
        val captureSettings = settings.map { it.toAVCapturePhotoSettings(output, options) }
        val result = CompletableDeferred<Unit>()
        output.setPreparedPhotoSettingsArray(captureSettings) { prepared, error ->
            when {
                error != null -> result.completeExceptionally(error.toException())
                prepared -> result.complete(Unit)
                else -> result.completeExceptionally(VisionCameraException("Settings preparation has been canceled!"))
            }
        }
        result.await()
    }

    override fun toString(): String = "CameraPhotoOutput($output)"
}
