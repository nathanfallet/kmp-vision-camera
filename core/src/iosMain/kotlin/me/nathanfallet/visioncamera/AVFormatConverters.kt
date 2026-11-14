package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureColorSpace
import platform.AVFoundation.AVCaptureColorSpace_AppleLog
import platform.AVFoundation.AVCaptureColorSpace_AppleLog2
import platform.AVFoundation.AVCaptureColorSpace_HLG_BT2020
import platform.AVFoundation.AVCaptureColorSpace_P3_D65
import platform.AVFoundation.AVCaptureColorSpace_sRGB
import platform.AVFoundation.AVCaptureVideoStabilizationMode
import platform.AVFoundation.AVCaptureVideoStabilizationModeAuto
import platform.AVFoundation.AVCaptureVideoStabilizationModeCinematic
import platform.AVFoundation.AVCaptureVideoStabilizationModeCinematicExtended
import platform.AVFoundation.AVCaptureVideoStabilizationModeCinematicExtendedEnhanced
import platform.AVFoundation.AVCaptureVideoStabilizationModeLowLatency
import platform.AVFoundation.AVCaptureVideoStabilizationModeOff
import platform.AVFoundation.AVCaptureVideoStabilizationModePreviewOptimized
import platform.AVFoundation.AVCaptureVideoStabilizationModeStandard
import platform.AVFoundation.AVFrameRateRange
import platform.CoreVideo.kCVPixelFormatType_16VersatileBayer
import platform.CoreVideo.kCVPixelFormatType_24RGB
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_32RGBA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr10BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.kCVPixelFormatType_422YpCbCr10BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_422YpCbCr10BiPlanarVideoRange
import platform.CoreVideo.kCVPixelFormatType_422YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_422YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.kCVPixelFormatType_444YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVPixelFormatType_444YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.kCVPixelFormatType_96VersatileBayerPacked12
import platform.CoreVideo.kCVPixelFormatType_DepthFloat16
import platform.CoreVideo.kCVPixelFormatType_DepthFloat32
import platform.CoreVideo.kCVPixelFormatType_DisparityFloat16
import platform.CoreVideo.kCVPixelFormatType_DisparityFloat32

// Port of V5's AV+PixelFormat.swift, CMFormatDescription.MediaSubType+hidden.swift, AV+ColorSpace.swift,
// AV+TargetColorSpace.swift, AVCaptureDevice.Format+bitDepth.swift, AVCaptureDevice.Format+colorRange.swift,
// AV+TargetStabilizationMode.swift and AV+Range.swift.

/** All known media sub types (`OSType` pixel formats), mapped to their [PixelFormat]. */
private val pixelFormatsByMediaSubType: Map<UInt, PixelFormat> = mapOf(
    // Depth
    kCVPixelFormatType_DepthFloat16 to DepthPixelFormat.DEPTH_16_BIT,
    kCVPixelFormatType_DepthFloat32 to DepthPixelFormat.DEPTH_32_BIT,
    // Disparity
    kCVPixelFormatType_DisparityFloat16 to DepthPixelFormat.DISPARITY_16_BIT,
    kCVPixelFormatType_DisparityFloat32 to DepthPixelFormat.DISPARITY_32_BIT,
    // YUV 4:2:0
    kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange to VideoPixelFormat.YUV_420_8_BIT_VIDEO,
    kCVPixelFormatType_420YpCbCr8BiPlanarFullRange to VideoPixelFormat.YUV_420_8_BIT_FULL,
    kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange to VideoPixelFormat.YUV_420_10_BIT_VIDEO,
    kCVPixelFormatType_420YpCbCr10BiPlanarFullRange to VideoPixelFormat.YUV_420_10_BIT_FULL,
    // YUV 4:2:2
    kCVPixelFormatType_422YpCbCr8BiPlanarVideoRange to VideoPixelFormat.YUV_422_8_BIT_VIDEO,
    kCVPixelFormatType_422YpCbCr8BiPlanarFullRange to VideoPixelFormat.YUV_422_8_BIT_FULL,
    kCVPixelFormatType_422YpCbCr10BiPlanarVideoRange to VideoPixelFormat.YUV_422_10_BIT_VIDEO,
    kCVPixelFormatType_422YpCbCr10BiPlanarFullRange to VideoPixelFormat.YUV_422_10_BIT_FULL,
    // YUV 4:4:4
    kCVPixelFormatType_444YpCbCr8BiPlanarVideoRange to VideoPixelFormat.YUV_444_8_BIT_VIDEO,
    kCVPixelFormatType_444YpCbCr8BiPlanarFullRange to VideoPixelFormat.YUV_444_8_BIT_FULL,
    // RGB
    kCVPixelFormatType_32BGRA to VideoPixelFormat.RGB_BGRA_8_BIT,
    kCVPixelFormatType_32RGBA to VideoPixelFormat.RGB_RGBA_8_BIT,
    kCVPixelFormatType_24RGB to VideoPixelFormat.RGB_RGB_8_BIT,
    // Bayer/RAW
    kCVPixelFormatType_96VersatileBayerPacked12 to VideoPixelFormat.RAW_BAYER_PACKED96_12_BIT,
    kCVPixelFormatType_16VersatileBayer to VideoPixelFormat.RAW_BAYER_UNPACKED_16_BIT,
)

/** Converts a media sub type (an `OSType` pixel format) to a [PixelFormat]. */
internal fun pixelFormatOf(mediaSubType: UInt): PixelFormat = pixelFormatsByMediaSubType[mediaSubType] ?: run {
    Logger.error("Unknown Format PixelFormat: \"${fourCharCodeToString(mediaSubType)}\"")
    VideoPixelFormat.UNKNOWN
}

@Suppress("MagicNumber")
private fun fourCharCodeToString(code: UInt): String = listOf(24, 16, 8, 0)
    .map { shift -> ((code shr shift) and 0xFFu).toInt().toChar() }
    .joinToString("")

/** The [DynamicRangeBitDepth] of the given media sub type. */
internal fun bitDepthOf(mediaSubType: UInt): DynamicRangeBitDepth = when (mediaSubType) {
    kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
    kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
    kCVPixelFormatType_422YpCbCr8BiPlanarVideoRange,
    kCVPixelFormatType_422YpCbCr8BiPlanarFullRange,
    -> DynamicRangeBitDepth.SDR_8_BIT

    kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange,
    kCVPixelFormatType_420YpCbCr10BiPlanarFullRange,
    kCVPixelFormatType_422YpCbCr10BiPlanarVideoRange,
    kCVPixelFormatType_422YpCbCr10BiPlanarFullRange,
    -> DynamicRangeBitDepth.HDR_10_BIT

    else -> DynamicRangeBitDepth.UNKNOWN
}

/** The [ColorRange] of the given media sub type. */
internal fun colorRangeOf(mediaSubType: UInt): ColorRange = when (mediaSubType) {
    kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
    kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange,
    kCVPixelFormatType_422YpCbCr8BiPlanarVideoRange,
    kCVPixelFormatType_422YpCbCr10BiPlanarVideoRange,
    -> ColorRange.VIDEO

    kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
    kCVPixelFormatType_420YpCbCr10BiPlanarFullRange,
    kCVPixelFormatType_422YpCbCr8BiPlanarFullRange,
    kCVPixelFormatType_422YpCbCr10BiPlanarFullRange,
    -> ColorRange.FULL

    else -> ColorRange.UNKNOWN
}

/** Converts an [AVCaptureColorSpace] to a [ColorSpace]. */
internal fun colorSpaceOf(colorSpace: AVCaptureColorSpace): ColorSpace = when (colorSpace) {
    AVCaptureColorSpace_sRGB -> ColorSpace.SRGB
    AVCaptureColorSpace_P3_D65 -> ColorSpace.P3_D65
    AVCaptureColorSpace_HLG_BT2020 -> ColorSpace.HLG_BT2020
    AVCaptureColorSpace_AppleLog -> ColorSpace.APPLE_LOG
    AVCaptureColorSpace_AppleLog2 -> ColorSpace.APPLE_LOG_2
    else -> ColorSpace.UNKNOWN
}

/** Converts a target [ColorSpace] to the closest [AVCaptureColorSpace] available on this iOS version. */
internal fun ColorSpace.toAVCaptureColorSpace(): AVCaptureColorSpace = when (this) {
    ColorSpace.SRGB, ColorSpace.UNKNOWN -> AVCaptureColorSpace_sRGB
    ColorSpace.P3_D65 -> AVCaptureColorSpace_P3_D65
    ColorSpace.HLG_BT2020 -> AVCaptureColorSpace_HLG_BT2020
    ColorSpace.APPLE_LOG ->
        if (isIOSAtLeast(major = 17)) AVCaptureColorSpace_AppleLog else AVCaptureColorSpace_HLG_BT2020
    ColorSpace.APPLE_LOG_2 -> when {
        isIOSAtLeast(major = 26) -> AVCaptureColorSpace_AppleLog2
        isIOSAtLeast(major = 17) -> AVCaptureColorSpace_AppleLog
        else -> AVCaptureColorSpace_HLG_BT2020
    }
    // Dolby Vision is not supported on iOS
    ColorSpace.DOLBY_VISION -> AVCaptureColorSpace_HLG_BT2020
}

/** Whether this [TargetDynamicRange] is a High Dynamic Range. */
internal val TargetDynamicRange.isHDR: Boolean
    get() = bitDepth == DynamicRangeBitDepth.HDR_10_BIT || colorSpace == ColorSpace.HLG_BT2020

/**
 * Converts a [TargetStabilizationMode] to the closest [AVCaptureVideoStabilizationMode] available on this iOS
 * version.
 */
internal fun TargetStabilizationMode.toAVCaptureVideoStabilizationMode(): AVCaptureVideoStabilizationMode =
    when (this) {
        TargetStabilizationMode.Off -> AVCaptureVideoStabilizationModeOff
        TargetStabilizationMode.Auto -> AVCaptureVideoStabilizationModeAuto
        StabilizationMode.STANDARD -> AVCaptureVideoStabilizationModeStandard
        StabilizationMode.CINEMATIC -> AVCaptureVideoStabilizationModeCinematic
        StabilizationMode.CINEMATIC_EXTENDED -> AVCaptureVideoStabilizationModeCinematicExtended
        StabilizationMode.CINEMATIC_EXTENDED_ENHANCED ->
            if (isIOSAtLeast(major = 18)) {
                AVCaptureVideoStabilizationModeCinematicExtendedEnhanced
            } else {
                AVCaptureVideoStabilizationModeCinematicExtended
            }
        StabilizationMode.PREVIEW_OPTIMIZED ->
            if (isIOSAtLeast(major = 17)) {
                AVCaptureVideoStabilizationModePreviewOptimized
            } else {
                AVCaptureVideoStabilizationModeStandard
            }
        StabilizationMode.LOW_LATENCY ->
            if (isIOSAtLeast(major = 26)) {
                AVCaptureVideoStabilizationModeLowLatency
            } else {
                AVCaptureVideoStabilizationModeStandard
            }
    }

/** Converts an [AVFrameRateRange] to a [Range]. */
internal fun AVFrameRateRange.toRange(): Range = Range(min = minFrameRate, max = maxFrameRate)
