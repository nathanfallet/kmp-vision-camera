package me.nathanfallet.visioncamera

import platform.AVFoundation.AVQueuedSampleBufferRenderingStatusFailed
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.AVFoundation.enqueueSampleBuffer
import platform.AVFoundation.flush
import platform.AVFoundation.requiresFlushToResumeDecoding
import platform.AVFoundation.status
import platform.QuartzCore.CALayer

/**
 * A [FrameRenderer] rendering into a [CALayer] (port of V5's `Public/NativeFrameRenderer.swift`).
 */
internal interface NativeFrameRenderer : FrameRenderer {
    /** The actual [CALayer] that should be added to the view. */
    val layer: CALayer
}

/**
 * The iOS [FrameRenderer], enqueuing [Frame]s into an [AVSampleBufferDisplayLayer] (port of V5's
 * `HybridFrameRenderer.swift`).
 */
internal class IosFrameRenderer : NativeFrameRenderer {
    override val layer = AVSampleBufferDisplayLayer()

    @Suppress("DEPRECATION", "ThrowsCount")
    override fun renderFrame(frame: Frame) {
        val nativeFrame = frame as? NativeFrame
            ?: throw VisionCameraException("Frame $frame is not of type `NativeFrame`!")
        val sampleBuffer = nativeFrame.sampleBuffer
            ?: throw VisionCameraException("Frame $frame does not have a valid `.sampleBuffer`!")

        layer.enqueueSampleBuffer(sampleBuffer)
        if (layer.requiresFlushToResumeDecoding) {
            layer.flush()
        }
        if (layer.status == AVQueuedSampleBufferRenderingStatusFailed) {
            throw VisionCameraException("Failed to render Frame!")
        }
    }
}
