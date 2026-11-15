package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.media.MediaActionSound
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.takePicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * The Android [CameraPhotoOutput], backed by a CameraX [ImageCapture] use-case.
 */
internal class AndroidPhotoOutput(
    private val options: PhotoOutputOptions,
) : CameraPhotoOutput, NativeCameraOutput {
    // V5 does not support depth and calibration data delivery for Photos on Android yet.
    override val supportsDepthDataDelivery: Boolean = false

    override val supportsCameraCalibrationDataDelivery: Boolean = false

    private var imageCapture: ImageCapture? = null
    private val shutterSound = MediaActionSound()

    override val mediaType: MediaType = MediaType.VIDEO

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            imageCapture?.targetRotation = value.surfaceRotation
        }

    override var mirrorMode: MirrorMode = MirrorMode.AUTO
        private set

    override val currentResolution: Size? get() = imageCapture?.resolutionInfo?.resolution?.toSize()

    init {
        // Pre-load the shutter sound
        CoroutineScope(Dispatchers.IO).launch { shutterSound.load(MediaActionSound.SHUTTER_CLICK) }
    }

    override fun createUseCase(
        mirrorMode: MirrorMode,
        config: NativeCameraOutput.Config,
    ): NativeCameraOutput.PreparedUseCase {
        val resolutionMode = when (options.qualityPrioritization) {
            QualityPrioritization.QUALITY -> ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
            else -> ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION
        }
        val targetSize = options.targetResolution.toAndroidSize()
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionFilter { sizes, _ -> sizes.sortedByClosestTo(targetSize) }
            .setAllowedResolutionMode(resolutionMode)
            .build()

        val outputFormat = options.containerFormat.toCameraXOutputFormat(allowHDR = config.photoHDR ?: false)
        val imageCapture = ImageCapture.Builder().apply {
            setResolutionSelector(resolutionSelector)
            setTargetRotation(outputOrientation.surfaceRotation)
            // Format (JPEG, RAW, ...)
            setOutputFormat(outputFormat)
            // Capture Mode (Quality, Speed, ...)
            setCaptureMode(options.qualityPrioritization.toCameraXCaptureMode())
            if (isProcessedFormat(outputFormat)) setJpegQuality(jpegQuality())
        }.build()

        return NativeCameraOutput.PreparedUseCase(imageCapture) {
            this.imageCapture = imageCapture
            this.mirrorMode = mirrorMode
        }
    }

    private fun isProcessedFormat(format: Int): Boolean =
        format == ImageCapture.OUTPUT_FORMAT_JPEG || format == ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR

    private fun jpegQuality(): Int {
        if (options.quality !in 0.0..1.0) {
            throw VisionCameraException(
                "Photo `quality` is not within 0.0 to 1.0 range! (Received: ${options.quality})",
            )
        }
        return (options.quality * JPEG_QUALITY_MAX).roundToInt().coerceAtLeast(1)
    }

    private fun requireImageCapture(): ImageCapture =
        imageCapture ?: throw VisionCameraException("Photo Output is not yet attached to the CameraSession!")

    @SuppressLint("RestrictedApi")
    private fun shouldMirror(imageCapture: ImageCapture): Boolean {
        val camera = imageCapture.camera ?: return false
        return when (mirrorMode) {
            MirrorMode.ON -> true
            MirrorMode.OFF -> false
            // AUTO follows CameraX's default "mirror front only" semantics.
            MirrorMode.AUTO -> camera.cameraInfo.lensFacing == CameraSelector.LENS_FACING_FRONT
        }
    }

    /** Applies the given [settings] to [imageCapture], and returns whether the shutter sound should be played. */
    private fun applySettings(imageCapture: ImageCapture, settings: CapturePhotoSettings): Boolean {
        imageCapture.flashMode = settings.flashMode.toCameraXFlashMode()
        return settings.enableShutterSound || CameraInfo.mustPlayShutterSound()
    }

    private fun onCaptureStarted(callbacks: CapturePhotoCallbacks, enableShutterSound: Boolean) {
        callbacks.onWillBeginCapture?.invoke()
        if (enableShutterSound) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        // CameraX exposes a single pre-capture hook, so we fire both the "will begin" and "will capture" callbacks
        // here. On iOS these are distinct moments (AVCapturePhotoCaptureDelegate).
        callbacks.onWillCapturePhoto?.invoke()
    }

    override suspend fun capturePhoto(
        settings: CapturePhotoSettings,
        callbacks: CapturePhotoCallbacks,
    ): Photo = withContext(Dispatchers.Main) {
        val imageCapture = requireImageCapture()
        Log.i(TAG, "Capturing Photo in-memory...")

        // 1. Get settings
        val isMirrored = shouldMirror(imageCapture)
        val enableShutterSound = applySettings(imageCapture, settings)
        val location = settings.location?.toAndroidLocation()

        // 2. Perform Capture
        var didFireOnDidCapturePhoto = false
        val image = imageCapture.takePicture(
            { onCaptureStarted(callbacks, enableShutterSound) },
            { progress ->
                if (progress == CAPTURE_PROGRESS_COMPLETE) {
                    // Capture is complete! Now we're encoding...
                    callbacks.onDidCapturePhoto?.invoke()
                    didFireOnDidCapturePhoto = true
                }
            },
            // Preview images (postview) are not supported yet.
        )
        // Not every device supports progress callbacks, so we just fire it later here
        if (!didFireOnDidCapturePhoto) callbacks.onDidCapturePhoto?.invoke()

        // 3. Return
        AndroidPhoto(image, isMirrored, location)
    }

    override suspend fun capturePhotoToFile(
        settings: CapturePhotoSettings,
        callbacks: CapturePhotoCallbacks,
    ): PhotoFile = withContext(Dispatchers.Main) {
        val imageCapture = requireImageCapture()
        Log.i(TAG, "Capturing Photo to file...")

        // 1. Get settings
        val isMirrored = shouldMirror(imageCapture)
        val enableShutterSound = applySettings(imageCapture, settings)
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("VisionCamera_", options.containerFormat.fileExtension)
        }
        val metadata = ImageCapture.Metadata().apply {
            location = settings.location?.toAndroidLocation()
            isReversedHorizontal = isMirrored
        }
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()

        // 2. Perform Capture
        var didFireOnDidCapturePhoto = false
        var timestamp: Double? = null
        val result = imageCapture.takePicture(
            outputFileOptions,
            {
                timestamp = SystemClock.elapsedRealtimeNanos() / NANOSECONDS_PER_SECOND
                onCaptureStarted(callbacks, enableShutterSound)
            },
            { progress ->
                if (progress == CAPTURE_PROGRESS_COMPLETE) {
                    // Capture is complete! Saving...
                    callbacks.onDidCapturePhoto?.invoke()
                    didFireOnDidCapturePhoto = true
                }
            },
            // Preview images (postview) are not supported yet.
        )
        // Not every device supports progress callbacks, so we just fire it later here
        if (!didFireOnDidCapturePhoto) callbacks.onDidCapturePhoto?.invoke()

        // 3. Return
        val captureTimestamp = timestamp
            ?: throw VisionCameraException("Photo capture did not report when exposure started!")
        withContext(Dispatchers.IO) {
            photoFileFromFile(file, result.imageFormat, captureTimestamp, isMirrored)
        }
    }

    // Preparing settings is not needed on Android.
    override suspend fun prepareSettings(settings: List<CapturePhotoSettings>) = Unit

    private companion object {
        const val JPEG_QUALITY_MAX = 100.0
        const val CAPTURE_PROGRESS_COMPLETE = 100
    }
}
