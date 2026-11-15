package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.camera.video.Recorder as CameraXRecorder

/**
 * The Android [CameraVideoOutput], backed by a CameraX [VideoCapture] use-case and its [CameraXRecorder].
 */
internal class AndroidVideoOutput(
    private val options: VideoOutputOptions,
) : CameraVideoOutput, NativeCameraOutput {
    private var videoCapture: VideoCapture<CameraXRecorder>? = null

    override val mediaType: MediaType = MediaType.VIDEO

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            videoCapture?.targetRotation = value.surfaceRotation
        }

    override val mirrorMode: MirrorMode
        get() = videoCapture?.let { mirrorModeFromCameraX(it.mirrorMode) } ?: MirrorMode.AUTO

    override val currentResolution: Size? get() = videoCapture?.resolutionInfo?.resolution?.toSize()

    private val videoCapabilitiesSource: Int
        get() = if (options.enableHigherResolutionCodecs) {
            // Use SOURCE_CODEC instead of CAMCORDER for resolutions/codecs: this allows using formats with higher
            // video resolution. Otherwise, a high-resolution video format may auto-fallback to a lower resolution
            // one that is supported by CAMCORDER.
            CameraXRecorder.VIDEO_CAPABILITIES_SOURCE_CODEC_CAPABILITIES
        } else {
            // CAMCORDER is a safe default for available qualities.
            CameraXRecorder.VIDEO_CAPABILITIES_SOURCE_CAMCORDER_PROFILE
        }

    private val recorder: CameraXRecorder = CameraXRecorder.Builder().apply {
        // Set custom target bit-rate.
        options.targetBitRate?.let { setTargetVideoEncodingBitRate(it.toInt()) }
        // Set the capabilities source, this can unlock higher resolutions.
        setVideoCapabilitiesSource(videoCapabilitiesSource)
        // Sets the Quality itself (UHD, FHD, ...). We intentionally do not call `setAspectRatio`: it's a hard filter
        // that can force CameraX to crop the chosen Quality's typical size, e.g. SD@640x480 + RATIO_16_9 → 640x360.
        setQualitySelector(options.targetResolution.toQualitySelector())
    }.build()

    override fun createUseCase(
        mirrorMode: MirrorMode,
        config: NativeCameraOutput.Config,
    ): NativeCameraOutput.PreparedUseCase {
        val videoCapture = VideoCapture.Builder(recorder).apply {
            setMirrorMode(mirrorMode.toCameraXMirrorMode())
            setTargetRotation(outputOrientation.surfaceRotation)
            // Dynamic Range (HDR, SDR, 10-bit, Dolby, ...)
            config.videoDynamicRange?.let { setDynamicRange(it.toCameraXDynamicRange()) }
            when (config.videoStabilizationMode) {
                // Unspecified: might be enabled, might be disabled.
                null, TargetStabilizationMode.Auto -> Unit
                TargetStabilizationMode.Off -> setVideoStabilizationEnabled(false)
                // Android only knows "on", no matter if it's STANDARD, CINEMATIC, etc.
                is StabilizationMode -> setVideoStabilizationEnabled(true)
            }
        }.build()
        return NativeCameraOutput.PreparedUseCase(videoCapture) {
            this.videoCapture = videoCapture
        }
    }

    // CameraX does not support getting the available codecs.
    override fun getSupportedVideoCodecs(): List<VideoCodec> = emptyList()

    // CameraX does not support setting custom output settings.
    override suspend fun setOutputSettings(settings: VideoOutputSettings) = Unit

    @SuppressLint("MissingPermission")
    override suspend fun createRecorder(settings: RecorderSettings): Recorder = withContext(Dispatchers.IO) {
        // Record to the given path, or to a .mp4 file in the temporary directory
        val file = settings.filePath?.let { File(it) } ?: File.createTempFile("VisionCamera_", ".mp4")
        // Create all parent directories if they don't exist yet.
        file.parentFile?.mkdirs()

        val fileOutputOptions = FileOutputOptions.Builder(file).apply {
            settings.location?.let { setLocation(it.toAndroidLocation()) }
            settings.maxDuration?.let { setDurationLimitMillis(it.inWholeMilliseconds) }
            settings.maxFileSize?.let { setFileSizeLimit(it) }
        }.build()
        var pendingRecording = recorder.prepareRecording(AndroidContext.context, fileOutputOptions)
        if (options.enableAudio) pendingRecording = pendingRecording.withAudioEnabled()
        if (options.enablePersistentRecorder) pendingRecording = pendingRecording.asPersistentRecording()

        AndroidRecorder(pendingRecording, file)
    }

    // Like V5, this output holds no native resource of its own: its CameraX `Recorder` is released with the use-case
    // once it is unbound, and recordings are owned (and stopped) by their `Recorder`.
    override fun dispose() = Unit
}
