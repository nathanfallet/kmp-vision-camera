package me.nathanfallet.visioncamera

import kotlin.time.Duration

/**
 * Why a [Recorder]'s recording finished. In every case, the video file is fully written and usable.
 */
enum class RecordingFinishedReason {
    /** The recording was stopped with [Recorder.stopRecording]. */
    STOPPED,

    /** The recording reached [RecorderSettings.maxDuration]. */
    MAX_DURATION_REACHED,

    /** The recording reached [RecorderSettings.maxFileSize]. */
    MAX_FILE_SIZE_REACHED,
}

/**
 * Records a single video to a file, see [CameraVideoOutput.createRecorder]. To record several videos, create
 * several [Recorder]s.
 */
interface Recorder {
    /** Whether this recorder is currently recording. */
    val isRecording: Boolean

    /** Whether the current recording is paused. */
    val isPaused: Boolean

    /** The duration recorded so far, or zero if not [isRecording]. */
    val recordedDuration: Duration

    /** The size of the file recorded so far, in bytes, or `0` if not [isRecording]. */
    val recordedFileSize: Long

    /** The absolute filesystem path this recorder records (or will record) to. */
    val filePath: String

    /**
     * Starts recording. Returns once the recording has started.
     *
     * @param onRecordingFinished Called with the file path once the recording finished, via [stopRecording] or a
     * limit of [RecorderSettings], see [RecordingFinishedReason].
     * @param onRecordingError Called if an unexpected error occurred while recording.
     * @param onRecordingPaused Called when the recording has been paused.
     * @param onRecordingResumed Called when the paused recording has been resumed.
     * @throws VisionCameraException If this recorder already recorded.
     */
    suspend fun startRecording(
        onRecordingFinished: (filePath: String, reason: RecordingFinishedReason) -> Unit,
        onRecordingError: (error: Throwable) -> Unit,
        onRecordingPaused: (() -> Unit)? = null,
        onRecordingResumed: (() -> Unit)? = null,
    )

    /**
     * Requests the recording to stop. `onRecordingFinished` is called once it actually stopped.
     *
     * @throws VisionCameraException If the recording is already stopped.
     */
    suspend fun stopRecording()

    /** Requests the recording to pause. `onRecordingPaused` is called once it actually paused. */
    suspend fun pauseRecording()

    /** Requests the paused recording to resume. `onRecordingResumed` is called once it actually resumed. */
    suspend fun resumeRecording()

    /** Cancels the current recording and deletes its file. */
    suspend fun cancelRecording()
}
