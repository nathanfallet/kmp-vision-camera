package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureFlashMode
import platform.AVFoundation.AVCaptureFlashModeAuto
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCapturePhotoQualityPrioritization
import platform.AVFoundation.AVCapturePhotoQualityPrioritizationBalanced
import platform.AVFoundation.AVCapturePhotoQualityPrioritizationQuality
import platform.AVFoundation.AVCapturePhotoQualityPrioritizationSpeed
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVFileTypeQuickTimeMovie
import platform.AVFoundation.AVVideoCodecTypeAppleProRes422
import platform.AVFoundation.AVVideoCodecTypeAppleProRes422HQ
import platform.AVFoundation.AVVideoCodecTypeAppleProRes422LT
import platform.AVFoundation.AVVideoCodecTypeAppleProRes422Proxy
import platform.AVFoundation.AVVideoCodecTypeAppleProRes4444
import platform.AVFoundation.AVVideoCodecTypeAppleProRes4444XQ
import platform.AVFoundation.AVVideoCodecTypeAppleProResRAW
import platform.AVFoundation.AVVideoCodecTypeAppleProResRAWHQ
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCodecTypeHEVC
import platform.AVFoundation.AVVideoCodecTypeHEVCWithAlpha
import platform.AVFoundation.AVVideoCodecTypeJPEG
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeDNG
import platform.UniformTypeIdentifiers.UTTypeHEIC
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypeMPEG4Movie
import platform.UniformTypeIdentifiers.UTTypeQuickTimeMovie
import platform.UniformTypeIdentifiers.UTTypeTIFF

// Port of V5's AV+FlashMode.swift, AV+QualityPrioritizaion.swift, AV+PhotoContainerFormat.swift,
// AV+RecorderFileType.swift and AV+VideoCodec.swift.

/** Converts a [FlashMode] to an [AVCaptureFlashMode]. */
internal fun FlashMode.toAVCaptureFlashMode(): AVCaptureFlashMode = when (this) {
    FlashMode.OFF -> AVCaptureFlashModeOff
    FlashMode.ON -> AVCaptureFlashModeOn
    FlashMode.AUTO -> AVCaptureFlashModeAuto
}

/** Converts a [QualityPrioritization] to an [AVCapturePhotoQualityPrioritization]. */
internal fun QualityPrioritization.toAVCapturePhotoQualityPrioritization(): AVCapturePhotoQualityPrioritization =
    when (this) {
        QualityPrioritization.SPEED -> AVCapturePhotoQualityPrioritizationSpeed
        QualityPrioritization.BALANCED -> AVCapturePhotoQualityPrioritizationBalanced
        QualityPrioritization.QUALITY -> AVCapturePhotoQualityPrioritizationQuality
    }

/** The [PhotoContainerFormat] photos are captured in for the given [TargetPhotoContainerFormat]. */
internal fun photoContainerFormatOf(targetFormat: TargetPhotoContainerFormat): PhotoContainerFormat =
    when (targetFormat) {
        // Apple's default Photo Format is HEIC
        TargetPhotoContainerFormat.NATIVE -> PhotoContainerFormat.HEIC
        TargetPhotoContainerFormat.JPEG -> PhotoContainerFormat.JPEG
        TargetPhotoContainerFormat.HEIC -> PhotoContainerFormat.HEIC
        TargetPhotoContainerFormat.DNG -> PhotoContainerFormat.DNG
    }

/**
 * Converts this [PhotoContainerFormat] to a [UTType].
 *
 * @throws VisionCameraException If this format has no [UTType] on this iOS version.
 */
internal fun PhotoContainerFormat.toUTType(): UTType = when (this) {
    PhotoContainerFormat.UNKNOWN ->
        throw VisionCameraException("Cannot convert \"unknown\" ImageFormat to AVFileType!")
    PhotoContainerFormat.JPEG -> UTTypeJPEG
    PhotoContainerFormat.HEIC -> UTTypeHEIC
    PhotoContainerFormat.TIFF -> UTTypeTIFF
    PhotoContainerFormat.DCM -> UTType.typeWithFilenameExtension("dcm")
        ?: throw VisionCameraException("PhotoContainerFormat \"dcm\" cannot be converted to UTType!")
    PhotoContainerFormat.DNG -> if (isIOSAtLeast(major = 18)) {
        UTTypeDNG
    } else {
        throw VisionCameraException("PhotoContainerFormat \"dng\" is only available on iOS 18.0 or newer!")
    }
}

/** Converts this [RecorderFileType] to an `AVFileType`. */
internal fun RecorderFileType.toAVFileType(): String? = when (this) {
    RecorderFileType.MP4 -> AVFileTypeMPEG4
    RecorderFileType.MOV -> AVFileTypeQuickTimeMovie
}

/** Converts this [RecorderFileType] to a [UTType]. */
internal fun RecorderFileType.toUTType(): UTType = when (this) {
    RecorderFileType.MP4 -> UTTypeMPEG4Movie
    RecorderFileType.MOV -> UTTypeQuickTimeMovie
}

/** Converts an `AVVideoCodecType` to a [VideoCodec]. */
@Suppress("CyclomaticComplexMethod")
internal fun videoCodecOf(codec: String?): VideoCodec = when {
    codec == AVVideoCodecTypeH264 -> VideoCodec.H264
    codec == AVVideoCodecTypeHEVC -> VideoCodec.H265
    codec == AVVideoCodecTypeHEVCWithAlpha -> VideoCodec.H265_WITH_ALPHA
    codec == AVVideoCodecTypeAppleProRes422Proxy -> VideoCodec.PRO_RES_422_PROXY
    codec == AVVideoCodecTypeAppleProRes422LT -> VideoCodec.PRO_RES_422_LT
    codec == AVVideoCodecTypeAppleProRes422 -> VideoCodec.PRO_RES_422
    codec == AVVideoCodecTypeAppleProRes422HQ -> VideoCodec.PRO_RES_422_HQ
    codec == AVVideoCodecTypeAppleProRes4444 -> VideoCodec.PRO_RES_4444
    codec == AVVideoCodecTypeJPEG -> VideoCodec.JPEG
    isIOSAtLeast(major = 18) && codec == AVVideoCodecTypeAppleProRes4444XQ -> VideoCodec.PRO_RES_4444_XQ
    isIOSAtLeast(major = 26) && codec == AVVideoCodecTypeAppleProResRAW -> VideoCodec.PRO_RES_RAW
    isIOSAtLeast(major = 26) && codec == AVVideoCodecTypeAppleProResRAWHQ -> VideoCodec.PRO_RES_RAW_HQ
    else -> {
        Logger.error("Received unknown AVVideoCodecType: $codec")
        VideoCodec.UNKNOWN
    }
}

/** Whether this [VideoCodec] is a RAW (ProRes) codec. */
internal val VideoCodec.isRawCodec: Boolean
    get() = when (this) {
        VideoCodec.PRO_RES_422_PROXY,
        VideoCodec.PRO_RES_422_LT,
        VideoCodec.PRO_RES_422,
        VideoCodec.PRO_RES_422_HQ,
        VideoCodec.PRO_RES_4444,
        VideoCodec.PRO_RES_4444_XQ,
        VideoCodec.PRO_RES_RAW_HQ,
        VideoCodec.PRO_RES_RAW,
        -> true
        else -> false
    }

/**
 * Converts this [VideoCodec] to an `AVVideoCodecType`.
 *
 * @throws VisionCameraException If this codec is not available on this iOS version.
 */
@Suppress("CyclomaticComplexMethod")
internal fun VideoCodec.toAVVideoCodecType(): String? = when (this) {
    VideoCodec.UNKNOWN -> throw VisionCameraException("VideoCodec \"unknown\" cannot be used as AVVideoCodecType!")
    VideoCodec.H264 -> AVVideoCodecTypeH264
    VideoCodec.H265 -> AVVideoCodecTypeHEVC
    VideoCodec.H265_WITH_ALPHA -> AVVideoCodecTypeHEVCWithAlpha
    VideoCodec.PRO_RES_422_PROXY -> AVVideoCodecTypeAppleProRes422Proxy
    VideoCodec.PRO_RES_422_LT -> AVVideoCodecTypeAppleProRes422LT
    VideoCodec.PRO_RES_422 -> AVVideoCodecTypeAppleProRes422
    VideoCodec.PRO_RES_422_HQ -> AVVideoCodecTypeAppleProRes422HQ
    VideoCodec.PRO_RES_4444 -> AVVideoCodecTypeAppleProRes4444
    VideoCodec.JPEG -> AVVideoCodecTypeJPEG
    VideoCodec.PRO_RES_4444_XQ -> if (isIOSAtLeast(major = 18)) {
        AVVideoCodecTypeAppleProRes4444XQ
    } else {
        throw VisionCameraException("VideoCodec \"$this\" is only available on iOS 18.0 or higher!")
    }
    VideoCodec.PRO_RES_RAW -> if (isIOSAtLeast(major = 26)) {
        AVVideoCodecTypeAppleProResRAW
    } else {
        throw VisionCameraException("VideoCodec \"$this\" is only available on iOS 26.0 or higher!")
    }
    VideoCodec.PRO_RES_RAW_HQ -> if (isIOSAtLeast(major = 26)) {
        AVVideoCodecTypeAppleProResRAWHQ
    } else {
        throw VisionCameraException("VideoCodec \"$this\" is only available on iOS 26.0 or higher!")
    }
}
