package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVErrorMaximumDurationReached
import platform.AVFoundation.AVErrorMaximumFileSizeReached
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * The [AVCaptureFileOutputRecordingDelegateProtocol] of a single recording (port of V5's `VideoDelegate.swift`).
 */
internal class VideoDelegate(
    private val onRecordingStarted: () -> Unit,
    private val onRecordingPaused: (() -> Unit)?,
    private val onRecordingResumed: (() -> Unit)?,
    private val onRecordingFinished: (NSURL, RecordingFinishedReason) -> Unit,
    private val onRecordingError: (Throwable) -> Unit,
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
    init {
        // Add a static strong reference while the recording is in progress
        ActiveDelegates.retain(this)
    }

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didStartRecordingToOutputFileAtURL: NSURL,
        startPTS: CValue<CMTime>,
        fromConnections: List<*>,
    ) {
        Logger.info("Recording started with timestamp: ${CMTimeGetSeconds(startPTS)}")
        onRecordingStarted()
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didStartRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
    ) {
        Logger.info("Recording started without timestamp.")
        onRecordingStarted()
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didPauseRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
    ) {
        onRecordingPaused?.invoke()
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didResumeRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
    ) {
        onRecordingResumed?.invoke()
    }

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        val reason = when {
            // No error, everything went according to plan we just stopped + finished
            error == null -> RecordingFinishedReason.STOPPED
            // We hit max duration limit - treat this as a success, not error!
            error.code == AVErrorMaximumDurationReached -> RecordingFinishedReason.MAX_DURATION_REACHED
            // We hit max file size limit - treat this as a success, not error!
            error.code == AVErrorMaximumFileSizeReached -> RecordingFinishedReason.MAX_FILE_SIZE_REACHED
            // We hit any other kind of error - this is an error event now.
            else -> null
        }
        if (reason != null) {
            onRecordingFinished(didFinishRecordingToOutputFileAtURL, reason)
        } else if (error != null) {
            onRecordingError(error.toException())
        }
        // Remove the static strong reference, we're done
        ActiveDelegates.release(this)
    }
}
