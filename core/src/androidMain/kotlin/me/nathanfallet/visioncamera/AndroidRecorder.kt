package me.nathanfallet.visioncamera

import androidx.camera.video.PendingRecording
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The Android [Recorder], recording a CameraX [PendingRecording] to a [file].
 *
 * Every state change happens on a serial executor, which also receives the CameraX recording events.
 */
internal class AndroidRecorder(
    private val pendingRecording: PendingRecording,
    private val file: File,
) : Recorder {
    // A serial executor whose thread stops once it is idle, so finished recorders don't leak a thread.
    private val executor = ThreadPoolExecutor(1, 1, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue()) {
        Thread(it, "me.nathanfallet.visioncamera.recorder")
    }.apply { allowCoreThreadTimeOut(true) }
    private val dispatcher = executor.asCoroutineDispatcher()

    @Volatile
    private var recording: Recording? = null

    @Volatile
    private var isCancelled = false

    @Volatile
    private var recordedDurationNanos = 0L

    @Volatile
    override var isPaused: Boolean = false
        private set

    @Volatile
    override var recordedFileSize: Long = 0L
        private set

    override val isRecording: Boolean get() = recording != null
    override val recordedDuration: Duration get() = recordedDurationNanos.nanoseconds
    override val filePath: String get() = file.absolutePath

    override suspend fun startRecording(
        onRecordingFinished: (filePath: String, reason: RecordingFinishedReason) -> Unit,
        onRecordingError: (error: Throwable) -> Unit,
        onRecordingPaused: (() -> Unit)?,
        onRecordingResumed: (() -> Unit)?,
    ) {
        val started = CompletableDeferred<Unit>()
        withContext(dispatcher) {
            // On our serial executor, make sure we are not currently recording
            if (recording != null) throw VisionCameraException("Active recording already in progress!")

            // Reset State
            resetState()

            // Start recording
            recording = pendingRecording.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> started.complete(Unit)
                    is VideoRecordEvent.Pause -> onRecordingPaused?.invoke()
                    is VideoRecordEvent.Resume -> onRecordingResumed?.invoke()
                    is VideoRecordEvent.Finalize -> onFinalize(event, started, onRecordingFinished, onRecordingError)
                    is VideoRecordEvent.Status -> {
                        recordedDurationNanos = event.recordingStats.recordedDurationNanos
                        recordedFileSize = event.recordingStats.numBytesRecorded
                    }
                    else -> Unit
                }
            }
        }
        // Returns once the recording actually started, or throws if it failed to start.
        started.await()
    }

    private fun onFinalize(
        event: VideoRecordEvent.Finalize,
        started: CompletableDeferred<Unit>,
        onRecordingFinished: (filePath: String, reason: RecordingFinishedReason) -> Unit,
        onRecordingError: (error: Throwable) -> Unit,
    ) {
        // Recording is over: drop the Recording reference so `isRecording` flips to false even when the finalize was
        // auto-triggered (e.g. by reaching `maxDuration` / `maxFileSize`) instead of via `stopRecording()`.
        recording = null
        if (isCancelled) {
            // Recording was cancelled: delete the file and don't notify
            isCancelled = false
            file.delete()
            return
        }
        val finishReason = when (event.error) {
            VideoRecordEvent.Finalize.ERROR_NONE -> RecordingFinishedReason.STOPPED
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> RecordingFinishedReason.MAX_DURATION_REACHED
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> RecordingFinishedReason.MAX_FILE_SIZE_REACHED
            // Source went inactive (e.g. Activity stopped mid-recording). Unlike ERROR_NO_VALID_DATA, CameraX still
            // finalizes a valid file, so keep it.
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> RecordingFinishedReason.STOPPED
            else -> null
        }
        if (finishReason != null) {
            // Recording finished successfully: either no error, or file/duration limit reached
            onRecordingFinished(file.absolutePath, finishReason)
        } else {
            // We have an error, either during capture or even while starting. If the recording did not even start
            // yet, `startRecording` throws it; otherwise it is an unknown error while recording.
            val error = VideoRecorderException(event.error, event.cause)
            if (!started.completeExceptionally(error)) onRecordingError(error)
        }
    }

    override suspend fun stopRecording() = withContext(dispatcher) {
        requireRecording().stop()
        recording = null
        resetState()
    }

    override suspend fun pauseRecording() = withContext(dispatcher) {
        requireRecording().pause()
        isPaused = true
    }

    override suspend fun resumeRecording() = withContext(dispatcher) {
        requireRecording().resume()
        isPaused = false
    }

    override suspend fun cancelRecording() = withContext(dispatcher) {
        val recording = requireRecording()
        // Mark as cancelled so the Finalize event deletes the file instead of notifying the caller
        isCancelled = true
        recording.close()
        this@AndroidRecorder.recording = null
        resetState()
    }

    private fun requireRecording(): Recording =
        recording ?: throw VisionCameraException("Not currently recording!")

    private fun resetState() {
        isPaused = false
        recordedDurationNanos = 0L
        recordedFileSize = 0L
    }

    private companion object {
        const val KEEP_ALIVE_SECONDS = 10L
    }
}
