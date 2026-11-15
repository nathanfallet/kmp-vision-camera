package me.nathanfallet.visioncamera.barcode.extensions

import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.NativeFrame
import me.nathanfallet.visioncamera.VisionCameraException
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreVideo.CVPixelBufferRef
import platform.UIKit.UIImageOrientation

// Port of V5's Extensions/ML+HybridFrameSpec.swift, for Apple Vision.

/** The pixel buffer of a [Frame] and the orientation of its content, to scan it with Vision. */
internal class VisionFrameInput(
    /** The frame's pixel buffer. */
    val pixelBuffer: CVPixelBufferRef,
    /** The orientation of the frame's content, combining its orientation and mirroring. */
    val orientation: UIImageOrientation,
)

/**
 * Gets the [VisionFrameInput] of this [Frame].
 *
 * @throws VisionCameraException If this [Frame] was not created by VisionCamera, or if it was disposed.
 */
internal fun Frame.toVisionInput(): VisionFrameInput {
    val nativeFrame = this as? NativeFrame ?: throw VisionCameraException("Frame is not of type `NativeFrame`!")
    val pixelBuffer = nativeFrame.sampleBuffer?.let { CMSampleBufferGetImageBuffer(it) }
        ?: throw VisionCameraException("Frame doesn't have a CMSampleBuffer with a CVPixelBuffer - it's invalid!")
    return VisionFrameInput(pixelBuffer = pixelBuffer, orientation = nativeFrame.metadata.uiImageOrientation)
}
