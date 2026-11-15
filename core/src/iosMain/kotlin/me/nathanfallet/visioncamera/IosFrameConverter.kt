package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import platform.AVFoundation.AVDepthData
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.kCIContextUseSoftwareRenderer
import platform.CoreMedia.CMCopyDictionaryOfAttachments
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.kCMAttachmentMode_ShouldPropagate
import platform.Foundation.CFBridgingRelease
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation

internal actual fun createPlatformFrameConverter(): FrameConverter = IosFrameConverter()

/**
 * The iOS [FrameConverter] (port of V5's `HybridFrameConverter.swift`), rendering the `CVPixelBuffer` of a
 * [NativeFrame] or the [AVDepthData] of a [NativeDepth] to a [UIImage] through CoreImage.
 */
internal class IosFrameConverter : FrameConverter {
    override fun convertFrameToImage(frame: Frame): Image {
        val nativeFrame = frame as? NativeFrame
            ?: throw VisionCameraException("The given `Frame` is not of type `NativeFrame`!")
        val sampleBuffer = nativeFrame.sampleBuffer
            ?: throw VisionCameraException("The given `Frame` has already been disposed!")
        val metadata = nativeFrame.metadata
        val uiOrientation = metadata.orientation.toUIImageOrientation(metadata.isMirrored)
        return IosImage(sampleBuffer.toUIImage(uiOrientation))
    }

    override fun convertDepthToImage(depth: Depth): Image {
        val nativeDepth = depth as? NativeDepth
            ?: throw VisionCameraException("The given `Depth` is not of type `NativeDepth`!")
        val depthData = nativeDepth.depthData
            ?: throw VisionCameraException("Cannot convert an already disposed Depth to an Image!")
        val metadata = nativeDepth.metadata
        val uiOrientation = metadata.orientation.toUIImageOrientation(metadata.isMirrored)
        return IosImage(depthData.toUIImage(uiOrientation))
    }
}

/** The shared, GPU-backed [CIContext] used to render images (port of V5's `CIImage+toUIImage.swift`). */
private val ciContext: CIContext by lazy {
    CIContext.contextWithOptions(mapOf<Any?, Any?>(kCIContextUseSoftwareRenderer to false))
}

/**
 * Renders this [CIImage] into a `CGImage` (a copy), and wraps it in a [UIImage] with the given [orientation].
 *
 * @throws VisionCameraException If the image cannot be rendered.
 */
private fun CIImage.toUIImage(orientation: UIImageOrientation): UIImage {
    // Copy the CIImage into a CGImage (render)
    val cgImage = ciContext.createCGImage(this, fromRect = extent)
        ?: throw VisionCameraException("Failed to copy CIImage into CGImage!")
    // No-copy wrap the CGImage in a UIImage, which retains it
    val uiImage = UIImage.imageWithCGImage(cgImage, scale = 1.0, orientation = orientation)
    CGImageRelease(cgImage)
    return uiImage
}

/**
 * Converts this sample buffer's pixel buffer to a [UIImage] (port of V5's `CMSampleBuffer+toUIImage.swift`), keeping
 * its attachments (color space, EXIF...).
 *
 * @throws VisionCameraException If this sample buffer has no pixel buffer, or cannot be rendered.
 */
@OptIn(BetaInteropApi::class)
private fun CMSampleBufferRef.toUIImage(orientation: UIImageOrientation): UIImage = autoreleasepool {
    val imageBuffer = CMSampleBufferGetImageBuffer(this)
        ?: throw VisionCameraException("This Frame does not have a PixelBuffer!")
    // Copy over any attachments (colorspace, EXIF, ...)
    val attachments = CMCopyDictionaryOfAttachments(kCFAllocatorDefault, this, kCMAttachmentMode_ShouldPropagate)
    @Suppress("UNCHECKED_CAST")
    val ciAttachments = attachments?.let { CFBridgingRelease(it) as? Map<Any?, *> }
    // No-copy create CIImage from CVPixelBuffer
    val ciImage = CIImage.imageWithCVPixelBuffer(imageBuffer, options = ciAttachments)
    // Convert CIImage to UIImage
    ciImage.toUIImage(orientation)
}

/**
 * Converts this depth data to a grey-scale [UIImage] (port of V5's `AVDepthData+toUIImage.swift`).
 *
 * @throws VisionCameraException If this depth data cannot be converted.
 */
@OptIn(BetaInteropApi::class)
private fun AVDepthData.toUIImage(orientation: UIImageOrientation): UIImage = autoreleasepool {
    // No-copy create CIImage from the AVDepthData's map. V5 uses `CIImage(depthData:)`, which wraps the same map, but
    // Kotlin/Native's CoreImage bindings only see a forward declaration of AVDepthData.
    val ciImage = CIImage.imageWithCVPixelBuffer(depthDataMap)
    // Convert CIImage to UIImage
    ciImage.toUIImage(orientation)
}
