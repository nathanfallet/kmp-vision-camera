package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFoundation.AVCaptureAudioDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTime
import platform.darwin.NSObject
import kotlin.concurrent.AtomicReference

/**
 * The [AVCaptureVideoDataOutputSampleBufferDelegateProtocol] of a video data output (port of V5's
 * `FrameDelegate.swift`). The buffer is only valid during [onFrame]: retain it to use it later.
 *
 * The callbacks can be set from any thread; they are called on the output's queue.
 */
internal class FrameDelegate : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val onFrameRef = AtomicReference<FrameCallback?>(null)
    private val onFrameDroppedRef = AtomicReference<((CMSampleBufferRef) -> Unit)?>(null)

    /** Called with each buffer, its presentation timestamp, and its connection's orientation and mirroring. */
    var onFrame: FrameCallback?
        get() = onFrameRef.value
        set(value) {
            onFrameRef.value = value
        }

    /** Called with each dropped buffer. */
    var onFrameDropped: ((CMSampleBufferRef) -> Unit)?
        get() = onFrameDroppedRef.value
        set(value) {
            onFrameDroppedRef.value = value
        }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didDropSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val buffer = didDropSampleBuffer ?: return
        onFrameDropped?.invoke(buffer)
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val buffer = didOutputSampleBuffer ?: return
        onFrame?.invoke(
            buffer,
            CMSampleBufferGetPresentationTimeStamp(buffer),
            fromConnection.bufferOrientation,
            fromConnection.videoMirrored,
        )
    }
}

/** The callback of a [FrameDelegate]: buffer, presentation timestamp, orientation and mirroring. */
internal typealias FrameCallback = (
    buffer: CMSampleBufferRef,
    timestamp: CValue<CMTime>,
    orientation: CameraOrientation,
    isMirrored: Boolean,
) -> Unit

/**
 * The [AVCaptureAudioDataOutputSampleBufferDelegateProtocol] of an audio data output (port of V5's
 * `AudioFrameDelegate.swift`). The buffer is only valid during [onFrame]: retain it to use it later.
 */
internal class AudioFrameDelegate : NSObject(), AVCaptureAudioDataOutputSampleBufferDelegateProtocol {
    var onFrame: ((CMSampleBufferRef) -> Unit)? = null

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val buffer = didOutputSampleBuffer ?: return
        onFrame?.invoke(buffer)
    }
}
