package me.nathanfallet.visioncamera

import kotlinx.cinterop.readValue
import kotlinx.coroutines.CompletableDeferred
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeInvalid
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_t
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * The iOS [Recorder] of a non-persistent [CameraVideoOutput], recording with an [AVCaptureMovieFileOutput] (port of
 * V5's `HybridVideoRecorder.swift`).
 */
internal class IosVideoRecorder(
    private val videoOutput: AVCaptureMovieFileOutput,
    private val queue: dispatch_queue_t,
    fileType: RecorderFileType,
    private val settings: RecorderSettings,
) : Recorder {
    override val filePath: String = settings.filePath ?: createTempFilePath(fileType.toUTType())

    /** Only accessed on the [queue]. */
    private var isCancelled = false

    init {
        // Create all parent directories if they don't exist yet.
        createParentDirectories(filePath)
    }

    override val isRecording: Boolean get() = videoOutput.recording

    override val isPaused: Boolean get() = isIOSAtLeast(major = 18) && videoOutput.recordingPaused

    override val recordedDuration: Duration
        get() = CMTimeGetSeconds(videoOutput.recordedDuration).takeIf { it.isFinite() }?.seconds ?: Duration.ZERO

    override val recordedFileSize: Long get() = videoOutput.recordedFileSize

    override suspend fun startRecording(
        onRecordingFinished: (filePath: String, reason: RecordingFinishedReason) -> Unit,
        onRecordingError: (error: Throwable) -> Unit,
        onRecordingPaused: (() -> Unit)?,
        onRecordingResumed: (() -> Unit)?,
    ) {
        val started = CompletableDeferred<Unit>()
        dispatch_async(queue) {
            // On our serial queue, make sure we are connected to the Camera
            if (videoOutput.connectionWithMediaType(AVMediaTypeVideo) == null) {
                started.completeExceptionally(
                    VisionCameraException("VideoOutput is not yet connected to the CameraSession!"),
                )
                return@dispatch_async
            }
            // On our serial queue, make sure we are currently not recording
            if (videoOutput.recording) {
                started.completeExceptionally(VisionCameraException("Active recording already in progress!"))
                return@dispatch_async
            }

            // Prepare the delegate to resolve and notify listeners
            val delegate = VideoDelegate(
                // Recording started!
                onRecordingStarted = { started.complete(Unit) },
                onRecordingPaused = onRecordingPaused,
                onRecordingResumed = onRecordingResumed,
                onRecordingFinished = { url, reason ->
                    if (isCancelled) {
                        // Recording was cancelled - delete the file
                        url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
                    } else {
                        // Recording finished!
                        onRecordingFinished(url.path ?: filePath, reason)
                    }
                },
                onRecordingError = { error ->
                    // If we failed to even start the video recording, reject the start. Otherwise, an error occurred
                    // later on.
                    if (!started.completeExceptionally(error)) onRecordingError(error)
                },
            )

            // Apply recording limits. `AVCaptureMovieFileOutput` delivers the file via the success path when either
            // limit is reached (see `VideoDelegate`).
            videoOutput.maxRecordedDuration = settings.maxDuration?.let {
                CMTimeMakeWithSeconds(it.toDouble(DurationUnit.SECONDS), preferredTimescale = DURATION_TIMESCALE)
            } ?: kCMTimeInvalid.readValue()
            videoOutput.maxRecordedFileSize = settings.maxFileSize ?: 0L

            // Start recording!
            videoOutput.startRecordingToOutputFileURL(NSURL.fileURLWithPath(filePath), recordingDelegate = delegate)
        }
        started.await()
    }

    override suspend fun stopRecording() = queue.await {
        videoOutput.stopRecording()
    }

    override suspend fun pauseRecording() = queue.await {
        if (!isIOSAtLeast(major = 18)) {
            throw VisionCameraException("`pauseRecording()` is only available on iOS 18 or above!")
        }
        videoOutput.pauseRecording()
    }

    override suspend fun resumeRecording() = queue.await {
        if (!isIOSAtLeast(major = 18)) {
            throw VisionCameraException("`resumeRecording()` is only available on iOS 18 or above!")
        }
        videoOutput.resumeRecording()
    }

    override suspend fun cancelRecording() = queue.await {
        if (!videoOutput.recording) throw VisionCameraException("Not currently recording!")
        // Mark as cancelled so the onRecordingFinished callback deletes the file instead of notifying the caller
        isCancelled = true
        videoOutput.stopRecording()
    }

    override fun toString(): String = "Recorder($filePath)"

    private companion object {
        const val DURATION_TIMESCALE = 600
    }
}
