package me.nathanfallet.visioncamera

import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFoundation.AVCaptureAudioDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.CoreMedia.CMSampleBufferRef
import platform.darwin.NSObject

/**
 * The [AVCaptureVideoDataOutputSampleBufferDelegateProtocol] of a video data output (port of V5's
 * `FrameDelegate.swift`). The buffer is only valid during [onFrame]: retain it to use it later.
 */
internal class FrameDelegate : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
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
