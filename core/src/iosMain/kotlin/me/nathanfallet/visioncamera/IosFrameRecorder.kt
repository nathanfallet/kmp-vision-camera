@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusFailed
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.movieTimeScale
import platform.AVFoundation.transform
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreMedia.CMClockGetTime
import platform.CoreMedia.CMClockRef
import platform.CoreMedia.CMFormatDescriptionRef
import platform.CoreMedia.CMSampleBufferGetTotalSampleSize
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Notified by an [IosFrameRecorder] when it starts and stops (port of V5's `RecorderDelegate.swift`).
 */
internal interface RecorderDelegate {
    fun onRecorderWillStart()

    fun onRecorderDidStop()
}

/**
 * The iOS [Recorder] of a persistent [CameraVideoOutput], writing sample buffers to an [AVAssetWriter] (port of V5's
 * `HybridFrameRecorder.swift`).
 *
 * @throws VisionCameraException If the [AVAssetWriter] cannot be created.
 */
@Suppress("TooManyFunctions")
internal class IosFrameRecorder(
    private val orientation: CameraOrientation,
    private val masterClock: CMClockRef,
    fileType: RecorderFileType,
    private val settings: RecorderSettings,
    private val delegate: RecorderDelegate,
) : Recorder {
    override val filePath: String = settings.filePath ?: createTempFilePath(fileType.toUTType())
    private val assetWriter: AVAssetWriter
    private var videoTrack: Track? = null
    private var audioTrack: Track? = null
    private val queue = createSerialQueue("me.nathanfallet.visioncamera.recorder", QOS_CLASS_UTILITY)
    private var isFinishing = false

    /**
     * Sum of all appended sample sizes. Accumulated rather than stat'd because `AVAssetWriter` buffers writes, so
     * on-disk size lags significantly behind the actual recorded data until `finishWriting` flushes.
     */
    private var accumulatedBytes = 0L

    private var onRecordingFinished: ((String, RecordingFinishedReason) -> Unit)? = null
    private var onRecordingError: ((Throwable) -> Unit)? = null
    private var onRecordingPaused: (() -> Unit)? = null
    private var onRecordingResumed: (() -> Unit)? = null

    /** Releases the retained [masterClock] and cancels the [assetWriter] if it is still writing (V5's `deinit`). */
    @Suppress("unused")
    private val cleaner: Cleaner

    private val isFinished: Boolean
        get() = (videoTrack?.isFinished ?: true) && (audioTrack?.isFinished ?: true)

    init {
        // Create all parent directories if they don't exist yet.
        createParentDirectories(filePath)
        assetWriter = throwingNSError("Failed to create the AVAssetWriter") { error ->
            AVAssetWriter(uRL = NSURL.fileURLWithPath(filePath), fileType = fileType.toAVFileType(), error = error)
        }
        assetWriter.shouldOptimizeForNetworkUse = false
        // Keep the clock alive even if its input is removed from the session (e.g. when switching Cameras).
        CFRetain(masterClock)
        cleaner = createCleaner(assetWriter to masterClock) { (writer, clock) ->
            if (writer.status == AVAssetWriterStatusWriting) {
                Logger.error("IosFrameRecorder was deallocated while writing, cancelling AssetWriter...")
                writer.cancelWriting()
            }
            CFRelease(clock)
        }
    }

    override val isRecording: Boolean get() = assetWriter.status == AVAssetWriterStatusWriting

    override val isPaused: Boolean get() = videoTrack?.isPaused == true

    override val recordedDuration: Duration
        get() = videoTrack?.duration?.seconds?.takeIf { it.isFinite() }?.seconds ?: Duration.ZERO

    override val recordedFileSize: Long
        get() {
            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(filePath, error = null)
            return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        }

    @Suppress("ThrowsCount")
    fun initializeVideoTrack(settings: Map<Any?, *>) {
        if (settings.isEmpty()) {
            throw VisionCameraException("Tried to initialize Video Track with empty options!")
        }
        if (videoTrack != null) throw VisionCameraException("Tried to initialize Video Track twice!")
        if (!assetWriter.canApplyOutputSettings(settings, forMediaType = AVMediaTypeVideo)) {
            throw VisionCameraException("The given output settings are not supported! $settings")
        }

        Logger.info("Initializing Video AssetWriter with settings: $settings")
        val videoWriter = AVAssetWriterInput(mediaType = AVMediaTypeVideo, outputSettings = settings)
        videoWriter.expectsMediaDataInRealTime = true
        videoWriter.transform = orientation.affineTransform
        assetWriter.addInput(videoWriter)
        videoTrack = Track(TrackType.VIDEO, videoWriter, masterClock)
        Logger.info("Initialized Video AssetWriter!")
    }

    fun setTimescale(timescale: Int) {
        Logger.info("Setting AVAssetWriter timescale to $timescale...")
        assetWriter.movieTimeScale = timescale
    }

    fun setMetadata(metadata: List<AVMetadataItem>) {
        Logger.info("Setting AVAssetWriter metadata: $metadata...")
        val items = metadata + libraryTagMetadataItem() + listOfNotNull(settings.location?.toAVMutableMetadataItem())
        assetWriter.metadata = items
    }

    fun initializeAudioTrack(settings: Map<Any?, *>?, format: CMFormatDescriptionRef) {
        if (audioTrack != null) throw VisionCameraException("Tried to initialize Audio Track twice!")

        if (settings != null) {
            Logger.info("Initializing Audio AssetWriter with settings: $settings")
        } else {
            Logger.info("Initializing Audio AssetWriter default settings...")
        }
        val audioWriter = AVAssetWriterInput(
            mediaType = AVMediaTypeAudio,
            outputSettings = settings,
            sourceFormatHint = format,
        )
        audioWriter.expectsMediaDataInRealTime = true
        assetWriter.addInput(audioWriter)
        audioTrack = Track(TrackType.AUDIO, audioWriter, masterClock)
        Logger.info("Initialized Audio AssetWriter!")
    }

    override suspend fun startRecording(
        onRecordingFinished: (filePath: String, reason: RecordingFinishedReason) -> Unit,
        onRecordingError: (error: Throwable) -> Unit,
        onRecordingPaused: (() -> Unit)?,
        onRecordingResumed: (() -> Unit)?,
    ): Unit = queue.await {
        if (isRecording) throw VisionCameraException("Tried starting Recorder twice!")

        // Notify about to start
        delegate.onRecorderWillStart()

        // Prepare the AssetWriter for writing to the video file
        if (!assetWriter.startWriting()) {
            val error = assetWriter.error?.localizedDescription ?: "(no error)"
            delegate.onRecorderDidStop()
            throw VisionCameraException("Failed to start AssetWriter! Error: $error")
        }
        Logger.info("AssetWriter started writing to $filePath!")

        // Assign callbacks
        this.onRecordingFinished = onRecordingFinished
        this.onRecordingError = onRecordingError
        this.onRecordingPaused = onRecordingPaused
        this.onRecordingResumed = onRecordingResumed

        // Start the session at the specific timestamp
        assetWriter.startSessionAtSourceTime(CMClockGetTime(masterClock))

        // Start each track
        videoTrack?.start()
        audioTrack?.start()
    }

    override suspend fun stopRecording(): Unit = queue.await {
        // Stop each track
        videoTrack?.stop()
        audioTrack?.stop()

        // Start a timeout that will force-stop the session if it still hasn't been stopped (maybe no more frames came
        // in?)
        val videoLatency = videoTrack?.latency?.seconds ?: 0.0
        val audioLatency = audioTrack?.latency?.seconds ?: 0.0
        val latency = maxOf(videoLatency, audioLatency)
        // wait 100ms by default
        val timeout = maxOf(latency * 2, DEFAULT_STOP_TIMEOUT_SECONDS).seconds
        queue.asyncAfter(timeout) {
            if (!isFinishing) {
                Logger.error("Waited $timeout, but the session is still not finished - force-stopping session...")
                finish(RecordingFinishedReason.STOPPED)
            }
        }
    }

    override suspend fun pauseRecording(): Unit = queue.await {
        // Pause each track
        videoTrack?.pause()
        audioTrack?.pause()
        // Notify listener
        onRecordingPaused?.invoke()
    }

    override suspend fun resumeRecording(): Unit = queue.await {
        // Resume each track
        videoTrack?.resume()
        audioTrack?.resume()
        // Notify listener
        onRecordingResumed?.invoke()
    }

    override suspend fun cancelRecording(): Unit = queue.await {
        if (assetWriter.status != AVAssetWriterStatusWriting) throw VisionCameraException("Not currently recording!")
        // Clear callbacks so they don't fire during cancellation
        onRecordingFinished = null
        onRecordingError = null
        onRecordingPaused = null
        onRecordingResumed = null
        // Cancel the writing session
        isFinishing = true
        assetWriter.cancelWriting()
        delegate.onRecorderDidStop()
        // Delete the temporary file
        throwingNSError("Failed to delete the recording file") { error ->
            NSFileManager.defaultManager.removeItemAtPath(filePath, error = error)
        }
    }

    /**
     * Appends the given [buffer] to the track of the given [type], asynchronously on the recorder's queue. The buffer
     * is retained until it was written.
     */
    fun append(buffer: CMSampleBufferRef, type: TrackType) {
        CFRetain(buffer)
        dispatch_async(queue) {
            try {
                appendOnQueue(buffer, type)
            } finally {
                CFRelease(buffer)
            }
        }
    }

    @Suppress("ReturnCount") // Faithful port of V5's guard clauses.
    private fun appendOnQueue(buffer: CMSampleBufferRef, type: TrackType) {
        // Session is already finishing, can't write anything more
        if (isFinishing) return
        if (assetWriter.status != AVAssetWriterStatusWriting) {
            val error = VisionCameraException("Frame arrived, but AssetWriter status is ${assetWriter.status}!")
            Logger.error("$error")
            onRecordingError?.invoke(error)
            return
        }

        // Write buffer to video/audio track
        try {
            getTrack(type).append(buffer)
            accumulatedBytes += CMSampleBufferGetTotalSampleSize(buffer).toLong()
        } catch (e: VisionCameraException) {
            Logger.error("Failed to append buffer to $type track! $e")
            onRecordingError?.invoke(e)
        }

        // If we failed to write the frames, stop the Recording
        if (assetWriter.status == AVAssetWriterStatusFailed) {
            Logger.error("Failed to write buffer: ${assetWriter.error}")
            val error = assetWriter.error?.toException() ?: VisionCameraException("Failed to write Buffer!")
            onRecordingError?.invoke(error)
            // finish() will hit the error guard below - reason is unused in that branch
            finish(RecordingFinishedReason.STOPPED)
            return
        }

        // Check if we reached any limits (file size or duration), if yes; stop. When all tracks (video + audio) are
        // finished (via `stopRecording`), finish the Recording.
        when {
            reachedDurationLimit() -> finish(RecordingFinishedReason.MAX_DURATION_REACHED)
            reachedFileSizeLimit() -> finish(RecordingFinishedReason.MAX_FILE_SIZE_REACHED)
            isFinished -> finish(RecordingFinishedReason.STOPPED)
        }
    }

    private fun reachedFileSizeLimit(): Boolean {
        val maxFileSize = settings.maxFileSize ?: return false
        // Check if video size >= max target file size
        val reached = accumulatedBytes >= maxFileSize
        if (reached) Logger.info("Reached maxFileSize of $maxFileSize bytes - finishing recording.")
        return reached
    }

    private fun reachedDurationLimit(): Boolean {
        val maxDuration = settings.maxDuration
        val duration = videoTrack?.duration
        if (maxDuration == null || duration == null) return false
        // Check if video track duration >= max target duration
        val maxTime = CMTimeMakeWithSeconds(maxDuration.toDouble(DurationUnit.SECONDS), DURATION_TIMESCALE)
        val reached = duration >= maxTime
        if (reached) Logger.info("Reached maxDuration of $maxDuration - finishing recording.")
        return reached
    }

    private fun getTrack(type: TrackType): Track = when (type) {
        TrackType.AUDIO -> audioTrack
            ?: throw VisionCameraException("Tried to write an audio buffer, but no audio track was initialized!")
        TrackType.VIDEO -> videoTrack
            ?: throw VisionCameraException("Tried to write a video buffer, but no video track was initialized!")
    }

    @Suppress("ReturnCount") // Faithful port of V5's guard clauses.
    private fun finish(reason: RecordingFinishedReason) {
        Logger.info("Stopping AssetWriter with status ${assetWriter.status}...")

        val lastVideoTimestamp: CValue<CMTime>? = videoTrack?.lastTimestamp
        if (lastVideoTimestamp == null) {
            // We don't even have a video track
            failFinish("Failed to finish() - No video track was ever initialized/started!")
            return
        }
        if (assetWriter.status != AVAssetWriterStatusWriting) {
            // The asset writer has an error - cancel everything.
            failFinish("Failed to finish() - AssetWriter status was ${assetWriter.status}!")
            return
        }
        if (isFinishing) {
            // We're already finishing - there was a second call to this method.
            Logger.error("Tried calling finish() twice!")
            return
        }

        isFinishing = true

        // End the session at the last video frame's timestamp. If there are audio frames after this timestamp, they
        // will be cut off.
        assetWriter.endSessionAtSourceTime(lastVideoTimestamp)
        Logger.info(
            "Asset Writer session stopped at ${lastVideoTimestamp.seconds}. (duration: $recordedDuration)",
        )
        assetWriter.finishWritingWithCompletionHandler {
            Logger.info("Asset Writer finished writing successfully!")
            onRecordingFinished?.invoke(filePath, reason)
            delegate.onRecorderDidStop()
        }
    }

    private fun failFinish(message: String) {
        val error = VisionCameraException(message)
        Logger.error(message)
        onRecordingError?.invoke(error)
        assetWriter.cancelWriting()
        delegate.onRecorderDidStop()
    }

    override fun toString(): String = "Recorder($filePath)"

    private companion object {
        const val DURATION_TIMESCALE = 600
        const val DEFAULT_STOP_TIMEOUT_SECONDS = 0.1
    }
}
