package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureAudioDataOutput
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVMediaTypeAudio
import platform.CoreMedia.CMSampleBufferRef
import platform.darwin.dispatch_async

/**
 * A separate audio-only [AVCaptureSession], recording the microphone for a persistent [Recorder] (port of V5's
 * `AudioSession.swift`).
 *
 * @throws VisionCameraException If no microphone is available, or if it cannot be added to the session.
 */
internal class AudioSession {
    private val captureSession = AVCaptureSession()

    // Create audio input
    val input: AVCaptureDeviceInput = createDeviceInput(
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
            ?: throw VisionCameraException("No microphone available!"),
    )

    // Create audio output (AVCaptureAudioDataOutput)
    val output = AVCaptureAudioDataOutput()

    // A DispatchQueue for our callbacks
    private val queue = createSerialQueue("me.nathanfallet.visioncamera.audio", QOS_CLASS_UTILITY)
    private val delegate = AudioFrameDelegate()

    init {
        Logger.info("Initializing AudioSession...")
        // Batch the coming changes
        captureSession.beginConfiguration()
        try {
            // Add audio input
            if (!captureSession.canAddInput(input)) {
                throw VisionCameraException("Microphone $input cannot be added to AVCaptureSession $captureSession!")
            }
            captureSession.addInput(input)

            // Add audio output
            if (!captureSession.canAddOutput(output)) {
                throw VisionCameraException(
                    "Audio output $output cannot be added to AVCaptureSession $captureSession!",
                )
            }
            captureSession.addOutput(output)

            // Add delegate/listener
            output.setSampleBufferDelegate(delegate, queue = queue)
        } finally {
            captureSession.commitConfiguration()
        }
    }

    fun setOnFrameListener(onFrame: (CMSampleBufferRef) -> Unit) {
        delegate.onFrame = onFrame
    }

    fun start() = dispatch_async(queue) {
        Logger.info("Starting AudioSession...")
        captureSession.startRunning()
    }

    fun stop() = dispatch_async(queue) {
        Logger.info("Stopping AudioSession...")
        captureSession.stopRunning()
    }
}
