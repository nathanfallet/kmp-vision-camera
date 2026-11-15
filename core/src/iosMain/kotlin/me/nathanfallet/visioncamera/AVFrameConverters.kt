package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureOutputDataDroppedReason
import platform.AVFoundation.AVCaptureOutputDataDroppedReasonDiscontinuity
import platform.AVFoundation.AVCaptureOutputDataDroppedReasonLateData
import platform.AVFoundation.AVCaptureOutputDataDroppedReasonOutOfBuffers
import platform.AVFoundation.AVDepthDataAccuracy
import platform.AVFoundation.AVDepthDataAccuracyAbsolute
import platform.AVFoundation.AVDepthDataAccuracyRelative
import platform.AVFoundation.AVDepthDataQuality
import platform.AVFoundation.AVDepthDataQualityHigh
import platform.AVFoundation.AVDepthDataQualityLow
import platform.CoreFoundation.CFRetain
import platform.CoreMedia.CMGetAttachment
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.kCMSampleBufferAttachmentKey_DroppedFrameReason
import platform.CoreMedia.kCMSampleBufferDroppedFrameReason_Discontinuity
import platform.CoreMedia.kCMSampleBufferDroppedFrameReason_FrameWasLate
import platform.CoreMedia.kCMSampleBufferDroppedFrameReason_OutOfBuffers
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_DepthFloat16
import platform.CoreVideo.kCVPixelFormatType_DepthFloat32
import platform.CoreVideo.kCVPixelFormatType_DisparityFloat16
import platform.CoreVideo.kCVPixelFormatType_DisparityFloat32
import platform.Foundation.CFBridgingRelease

// Port of V5's AV+DepthDataAccuracy.swift, AV+DepthDataQuality.swift, AV+DepthPixelFormat.swift,
// AV+FrameDroppedReason.swift, AV+TargetVideoPixelFormat.swift and Utils/TargetCVPixelFormat.swift.

/** Converts an [AVDepthDataAccuracy] to a [DepthDataAccuracy]. */
internal fun depthDataAccuracyOf(accuracy: AVDepthDataAccuracy): DepthDataAccuracy = when (accuracy) {
    AVDepthDataAccuracyAbsolute -> DepthDataAccuracy.ABSOLUTE
    AVDepthDataAccuracyRelative -> DepthDataAccuracy.RELATIVE
    else -> DepthDataAccuracy.UNKNOWN
}

/** Converts an [AVDepthDataQuality] to a [DepthDataQuality]. */
internal fun depthDataQualityOf(quality: AVDepthDataQuality): DepthDataQuality = when (quality) {
    AVDepthDataQualityLow -> DepthDataQuality.LOW
    AVDepthDataQualityHigh -> DepthDataQuality.HIGH
    else -> DepthDataQuality.UNKNOWN
}

/** Converts an `OSType` depth pixel format to a [DepthPixelFormat]. */
internal fun depthPixelFormatOf(osType: UInt): DepthPixelFormat = when (osType) {
    kCVPixelFormatType_DepthFloat16 -> DepthPixelFormat.DEPTH_16_BIT
    kCVPixelFormatType_DepthFloat32 -> DepthPixelFormat.DEPTH_32_BIT
    kCVPixelFormatType_DisparityFloat16 -> DepthPixelFormat.DISPARITY_16_BIT
    kCVPixelFormatType_DisparityFloat32 -> DepthPixelFormat.DISPARITY_32_BIT
    else -> DepthPixelFormat.UNKNOWN
}

/**
 * Converts this [DepthPixelFormat] to an `OSType` depth pixel format.
 *
 * @throws VisionCameraException If this format is not supported on iOS.
 */
internal fun DepthPixelFormat.toCVPixelFormatType(): UInt = when (this) {
    DepthPixelFormat.UNKNOWN ->
        throw VisionCameraException("Cannot convert DepthPixelFormat \"unknown\" to native OSType!")
    DepthPixelFormat.DEPTH_POINT_CLOUD_32_BIT ->
        throw VisionCameraException("DepthPixelFormat \"depth-point-cloud-32-bit\" is not supported on iOS!")
    DepthPixelFormat.DEPTH_16_BIT -> kCVPixelFormatType_DepthFloat16
    DepthPixelFormat.DEPTH_32_BIT -> kCVPixelFormatType_DepthFloat32
    DepthPixelFormat.DISPARITY_16_BIT -> kCVPixelFormatType_DisparityFloat16
    DepthPixelFormat.DISPARITY_32_BIT -> kCVPixelFormatType_DisparityFloat32
}

/** Converts an [AVCaptureOutputDataDroppedReason] to a [FrameDroppedReason]. */
internal fun frameDroppedReasonOf(reason: AVCaptureOutputDataDroppedReason): FrameDroppedReason = when (reason) {
    AVCaptureOutputDataDroppedReasonLateData -> FrameDroppedReason.FRAME_WAS_LATE
    AVCaptureOutputDataDroppedReasonOutOfBuffers -> FrameDroppedReason.OUT_OF_BUFFERS
    AVCaptureOutputDataDroppedReasonDiscontinuity -> FrameDroppedReason.DISCONTINUITY
    else -> FrameDroppedReason.UNKNOWN
}

/**
 * Reads the [FrameDroppedReason] of a dropped [sampleBuffer] from its `kCMSampleBufferAttachmentKey_DroppedFrameReason`
 * attachment, or `null` if it has none.
 */
internal fun frameDroppedReasonOfSampleBuffer(sampleBuffer: CMSampleBufferRef): FrameDroppedReason? {
    val attachment = CMGetAttachment(sampleBuffer, kCMSampleBufferAttachmentKey_DroppedFrameReason, null)
        ?: return null
    return when (CFBridgingRelease(CFRetain(attachment)) as? String) {
        kCMSampleBufferDroppedFrameReason_FrameWasLate.toKotlinString() -> FrameDroppedReason.FRAME_WAS_LATE
        kCMSampleBufferDroppedFrameReason_OutOfBuffers.toKotlinString() -> FrameDroppedReason.OUT_OF_BUFFERS
        kCMSampleBufferDroppedFrameReason_Discontinuity.toKotlinString() -> FrameDroppedReason.DISCONTINUITY
        else -> FrameDroppedReason.UNKNOWN
    }
}

/**
 * The `CVPixelFormatType` a [TargetVideoPixelFormat] resolves to (V5's `TargetCVPixelFormat`).
 */
internal sealed interface TargetCVPixelFormat {
    /** The device-native format (most efficient). */
    data object Native : TargetCVPixelFormat

    /** A specific `OSType` [format]. */
    data class Specific(val format: UInt) : TargetCVPixelFormat
}

/** Converts this [TargetVideoPixelFormat] to a [TargetCVPixelFormat]. */
internal fun TargetVideoPixelFormat.toCVPixelFormatType(): TargetCVPixelFormat = when (this) {
    TargetVideoPixelFormat.NATIVE -> TargetCVPixelFormat.Native
    TargetVideoPixelFormat.YUV -> TargetCVPixelFormat.Specific(kCVPixelFormatType_420YpCbCr8BiPlanarFullRange)
    TargetVideoPixelFormat.RGB -> TargetCVPixelFormat.Specific(kCVPixelFormatType_32BGRA)
}
