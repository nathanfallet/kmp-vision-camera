package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeHEVC
import platform.AVFoundation.AVVideoCodecTypeJPEG
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoQualityKey
import platform.Foundation.NSNumber
import platform.ImageIO.kCGImagePropertyGPSDictionary
import platform.ImageIO.kCGImagePropertyTIFFDictionary
import platform.ImageIO.kCGImagePropertyTIFFSoftware

// Port of V5's AV+CapturePhotoSettings.swift. Depth data, calibration data and preview images are not part of the
// common API yet, so their settings are not ported.

/** Whether these settings capture a RAW photo. */
private val AVCapturePhotoSettings.isRAWFormat: Boolean
    get() = rawPhotoPixelFormatType != 0u

/** Whether these settings capture an Apple ProRAW photo. */
private val AVCapturePhotoSettings.isAppleProRAWFormat: Boolean
    get() = AVCapturePhotoOutput.isAppleProRAWPixelFormat(rawPhotoPixelFormatType)

/** Whether these settings support `photoQualityPrioritization`: processed formats and Apple ProRAW only. */
private val AVCapturePhotoSettings.supportsQualityPrioritization: Boolean
    get() = if (isRAWFormat) isAppleProRAWFormat else true

/** Whether these settings support distortion correction: if we shoot in RAW, we cannot enable it. */
private val AVCapturePhotoSettings.supportsDistortionCorrection: Boolean
    get() = !isRAWFormat

/** All RAW pixel formats available on this output. */
private val AVCapturePhotoOutput.rawPixelFormats: List<UInt>
    get() = availableRawPhotoPixelFormatTypes.mapNotNull { (it as? NSNumber)?.unsignedIntValue ?: (it as? UInt) }

/**
 * The best RAW pixel format of this output: Apple ProRAW if enabled and available, otherwise the first available
 * default RAW format (often Bayer14), or `null` if there is none.
 */
private val AVCapturePhotoOutput.bestRawPixelFormat: UInt?
    get() {
        val appleProRawFormat = if (appleProRAWEnabled) {
            rawPixelFormats.firstOrNull { AVCapturePhotoOutput.isAppleProRAWPixelFormat(it) }
        } else {
            null
        }
        return appleProRawFormat ?: rawPixelFormats.firstOrNull()
    }

/** The flash modes supported by this output. */
private val AVCapturePhotoOutput.supportedFlashModeValues: List<Long>
    get() = supportedFlashModes.mapNotNull { (it as? NSNumber)?.longValue ?: (it as? Long) }

private fun baseAVCapturePhotoSettings(
    output: AVCapturePhotoOutput,
    containerFormat: TargetPhotoContainerFormat,
    quality: Double,
): AVCapturePhotoSettings {
    if (quality !in 0.0..1.0) {
        throw VisionCameraException("Photo `quality` is not within 0.0 to 1.0 range! (Received: $quality)")
    }
    return when (containerFormat) {
        TargetPhotoContainerFormat.JPEG -> AVCapturePhotoSettings.photoSettingsWithFormat(
            mapOf<Any?, Any?>(
                AVVideoCodecKey to AVVideoCodecTypeJPEG,
                AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(AVVideoQualityKey to quality),
            ),
        )
        TargetPhotoContainerFormat.HEIC -> AVCapturePhotoSettings.photoSettingsWithFormat(
            mapOf<Any?, Any?>(
                AVVideoCodecKey to AVVideoCodecTypeHEVC,
                AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(AVVideoQualityKey to quality),
            ),
        )
        TargetPhotoContainerFormat.DNG -> {
            // For RAW, we need to get the first available RAW format (often Bayer14)
            val rawFormat = output.bestRawPixelFormat ?: throw VisionCameraException(
                "This PhotoOutput does not support raw capture (\"dng\" format)! Select a Format that contains " +
                    "`dng` in its `supportedPhotoContainerFormats` to capture raw photos.",
            )
            AVCapturePhotoSettings.photoSettingsWithRawPixelFormatType(rawFormat)
        }
        // Apple's default processed photo format is HEIC.
        TargetPhotoContainerFormat.NATIVE ->
            baseAVCapturePhotoSettings(output, TargetPhotoContainerFormat.HEIC, quality)
    }
}

/**
 * Converts these [CapturePhotoSettings] to [AVCapturePhotoSettings] for the given [output], with some sensible
 * defaults:
 * - Options that are supported are applied.
 * - Options that are not supported but don't directly affect results (e.g. `enableDistortionCorrection`) are
 *   swallowed.
 *
 * @throws VisionCameraException If the settings cannot be created (e.g. RAW is not supported).
 */
internal fun CapturePhotoSettings.toAVCapturePhotoSettings(
    output: AVCapturePhotoOutput,
    options: PhotoOutputOptions,
): AVCapturePhotoSettings {
    val settings = baseAVCapturePhotoSettings(output, options.containerFormat, options.quality)

    // Shoot in max resolution
    if (isIOSAtLeast(major = 16)) settings.maxPhotoDimensions = output.maxPhotoDimensions

    // photoQualityPrioritization can only be set on processed photos. It is configured on the PhotoOutput itself.
    if (settings.supportsQualityPrioritization) {
        settings.photoQualityPrioritization = output.maxPhotoQualityPrioritization
    }
    // flashMode
    val avFlashMode = flashMode.toAVCaptureFlashMode()
    if (avFlashMode in output.supportedFlashModeValues) settings.flashMode = avFlashMode
    // enableShutterSound
    if (isIOSAtLeast(major = 18) && output.shutterSoundSuppressionSupported) {
        settings.shutterSoundSuppressionEnabled = !enableShutterSound
    }
    // enableRedEyeReduction
    if (output.autoRedEyeReductionSupported) settings.autoRedEyeReductionEnabled = enableRedEyeReduction
    // enableDistortionCorrection
    if (settings.supportsDistortionCorrection && output.contentAwareDistortionCorrectionSupported) {
        settings.autoContentAwareDistortionCorrectionEnabled = enableDistortionCorrection
    }
    // enableVirtualDeviceFusion
    if (output.virtualDeviceFusionSupported) settings.autoVirtualDeviceFusionEnabled = enableVirtualDeviceFusion

    // metadata
    settings.addLibraryMetadataTag()
    location?.let { settings.addLocationMetadataTag(it) }
    return settings
}

private fun AVCapturePhotoSettings.addLibraryMetadataTag() {
    val metadataCopy = metadata.toMutableMap()
    val tiffKey = kCGImagePropertyTIFFDictionary.toKotlinString()
    @Suppress("UNCHECKED_CAST")
    val tiff = (metadataCopy[tiffKey] as? Map<Any?, Any?>)?.toMutableMap() ?: mutableMapOf()
    tiff[kCGImagePropertyTIFFSoftware.toKotlinString()] = "VisionCamera"
    metadataCopy[tiffKey] = tiff
    metadata = metadataCopy
}

private fun AVCapturePhotoSettings.addLocationMetadataTag(location: Location) {
    val metadataCopy = metadata.toMutableMap()
    metadataCopy[kCGImagePropertyGPSDictionary.toKotlinString()] = location.toEXIF()
    metadata = metadataCopy
}
