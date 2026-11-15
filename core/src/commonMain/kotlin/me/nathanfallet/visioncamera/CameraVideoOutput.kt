package me.nathanfallet.visioncamera

import kotlin.time.Duration

/**
 * The container file type of a [CameraVideoOutput]'s recordings.
 */
enum class RecorderFileType {
    /** MPEG-4 (`.mp4`). */
    MP4,

    /** QuickTime (`.mov`). */
    MOV,
}

/**
 * Configuration options of a [CameraVideoOutput], see [CameraFactory.createVideoOutput].
 */
data class VideoOutputOptions(
    /**
     * The target video resolution. The [CameraSession] negotiates it with the other outputs and constraints, so it
     * may not be met exactly; its aspect ratio is prioritized over its pixel count.
     */
    val targetResolution: Size = CommonResolutions.FHD_16_9,
    /** Whether to record audio. Requires the microphone permission. */
    val enableAudio: Boolean = false,
    /**
     * Whether the recorder is persistent, i.e. keeps recording while switching cameras. It may use a less native
     * recording path: an `AVCaptureVideoDataOutput` + `AVAssetWriter` pipeline on iOS, `asPersistentRecording()` on
     * Android.
     */
    val enablePersistentRecorder: Boolean = false,
    /**
     * Whether to allow higher resolution (and possibly higher bit-rate and frame rate) codecs, at the risk of higher
     * power usage and device-specific instability. Android only.
     */
    val enableHigherResolutionCodecs: Boolean = false,
    /** The target bit-rate, in bits per second, or `null` for the encoder's default. */
    val targetBitRate: Double? = null,
    /** The container file type. Android always records `.mp4` and ignores it. */
    val fileType: RecorderFileType = RecorderFileType.MOV,
)

/**
 * Output settings of a [CameraVideoOutput], see [CameraVideoOutput.setOutputSettings].
 */
data class VideoOutputSettings(
    /** The [VideoCodec] to record in, or `null` to pick the most efficient one (likely H.265). */
    val codec: VideoCodec? = null,
)

/**
 * Settings of a [Recorder], see [CameraVideoOutput.createRecorder].
 */
data class RecorderSettings(
    /** The [Location] to embed in the video's metadata (ISO-6709). */
    val location: Location? = null,
    /**
     * The absolute filesystem path (including file name and extension) to record to, or `null` to record to a
     * temporary file. Missing parent directories are created.
     */
    val filePath: String? = null,
    /** If set, the recording stops by itself once it reaches this duration. */
    val maxDuration: Duration? = null,
    /** If set, the recording stops by itself once the file reaches this size, in bytes. */
    val maxFileSize: Long? = null,
)

/**
 * A [CameraOutput] recording videos (possibly with audio) to files, through [Recorder]s.
 *
 * ```kotlin
 * val videoOutput = VisionCamera.createVideoOutput(VideoOutputOptions(enableAudio = true))
 * // ... connect it to a CameraSession, then:
 * val recorder = videoOutput.createRecorder()
 * recorder.startRecording(
 *     onRecordingFinished = { filePath, reason -> println("Saved to $filePath ($reason)") },
 *     onRecordingError = { error -> println("Recording failed: $error") },
 * )
 * // ... later:
 * recorder.stopRecording()
 * ```
 */
interface CameraVideoOutput : CameraOutput {
    /**
     * Gets the [VideoCodec]s this output can currently record in. Only supported on iOS.
     *
     * @throws VisionCameraException If this output is not connected to a [CameraSession] yet.
     */
    fun getSupportedVideoCodecs(): List<VideoCodec>

    /**
     * Sets the output settings of this output, after it is connected to a [CameraSession] and before
     * [createRecorder]. Only supported on iOS.
     */
    suspend fun setOutputSettings(settings: VideoOutputSettings)

    /**
     * Creates and prepares a new [Recorder] with the given [settings]. A [Recorder] records once: create a new one
     * for every recording.
     */
    suspend fun createRecorder(settings: RecorderSettings = RecorderSettings()): Recorder
}
