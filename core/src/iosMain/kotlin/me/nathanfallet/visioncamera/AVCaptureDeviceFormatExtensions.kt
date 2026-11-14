package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.CMVideoDimensionsValue
import platform.CoreMedia.CMFormatDescriptionGetMediaSubType
import platform.CoreMedia.CMVideoDimensions
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSNumber
import platform.Foundation.NSValue

// Port of V5's AVCaptureDevice.Format+supportedResolutionsFor.swift, AVCaptureDevice.Format+supportsOutput.swift,
// AVCaptureDevice.Format+bitDepth.swift and AVCaptureDevice.Format+colorRange.swift.

/** Converts [CMVideoDimensions] to a [Size]. */
internal fun CValue<CMVideoDimensions>.toSize(): Size = useContents { Size(width = width, height = height) }

/** The media sub type (the `OSType` pixel format) of this format. */
internal val AVCaptureDeviceFormat.mediaSubType: UInt
    get() = CMFormatDescriptionGetMediaSubType(formatDescription)

/** The [PixelFormat] of this format. */
internal val AVCaptureDeviceFormat.pixelFormat: PixelFormat
    get() = pixelFormatOf(mediaSubType)

/** The [DynamicRangeBitDepth] of this format's pixel format. */
internal val AVCaptureDeviceFormat.bitDepth: DynamicRangeBitDepth
    get() = bitDepthOf(mediaSubType)

/** The [ColorRange] of this format's pixel format. */
internal val AVCaptureDeviceFormat.colorRange: ColorRange
    get() = colorRangeOf(mediaSubType)

/** All supported `AVCaptureColorSpace`s of this format. */
internal val AVCaptureDeviceFormat.colorSpaces: List<Long>
    get() = supportedColorSpaces.filterIsInstance<NSNumber>().map { it.longValue }

/** All supported frame rate ranges of this format. */
internal val AVCaptureDeviceFormat.frameRateRanges: List<AVFrameRateRange>
    get() = videoSupportedFrameRateRanges.filterIsInstance<AVFrameRateRange>()

/** All supported depth data formats of this format. */
internal val AVCaptureDeviceFormat.depthDataFormats: List<AVCaptureDeviceFormat>
    get() = supportedDepthDataFormats.filterIsInstance<AVCaptureDeviceFormat>()

private val AVCaptureDeviceFormat.videoDimensions: List<Size>
    get() = listOf(CMVideoFormatDescriptionGetDimensions(formatDescription).toSize())

@Suppress("DEPRECATION")
private val AVCaptureDeviceFormat.photoDimensions: List<Size>
    get() = if (isIOSAtLeast(major = 16)) {
        supportedMaxPhotoDimensions.filterIsInstance<NSValue>().map { it.CMVideoDimensionsValue.toSize() }
    } else {
        listOf(highResolutionStillImageDimensions.toSize())
    }

/**
 * Get all supported resolutions for the given [streamType].
 */
internal fun AVCaptureDeviceFormat.supportedResolutions(streamType: StreamType): List<Size> = when (streamType) {
    StreamType.VIDEO -> videoDimensions
    StreamType.PHOTO -> photoDimensions
    StreamType.DEPTH_VIDEO -> depthDataFormats.flatMap { it.videoDimensions }.distinct()
    StreamType.DEPTH_PHOTO -> depthDataFormats.flatMap { it.photoDimensions }.distinct()
}

/**
 * Whether this format supports streaming to the given [output] (i.e. its class is not listed in
 * `unsupportedCaptureOutputClasses`).
 */
@OptIn(BetaInteropApi::class)
internal fun AVCaptureDeviceFormat.supportsOutput(output: AVCaptureOutput): Boolean =
    unsupportedCaptureOutputClasses.filterIsInstance<ObjCClass>().none { output.isMemberOfClass(it) }
