package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDeviceFormat

// Port of V5's ResolvableConstraint+VideoDynamicRangeConstraint.swift.

/**
 * Resolves this target dynamic range against the [format]'s actual capabilities. Returns the achievable dynamic
 * range (using the format's actual bit depth + color range, and the best available color space from the fallback
 * chain).
 */
internal fun TargetDynamicRange.resolve(format: AVCaptureDeviceFormat): ConstraintResolution<TargetDynamicRange> {
    val actualBitDepth = format.bitDepth
    val bitDepthPenalty = bitDepthPenalty(target = bitDepth, actual = actualBitDepth)

    val colorSpaceResolution = colorSpace.resolve(format)

    val actualColorRange = format.colorRange
    val colorRangePenalty = colorRangePenalty(target = colorRange, actual = actualColorRange)

    val totalPenalty = bitDepthPenalty + colorSpaceResolution.penalty + colorRangePenalty

    // Build the resolved dynamic range from what the format actually supports
    val resolvedDynamicRange = TargetDynamicRange(
        bitDepth = if (actualBitDepth == DynamicRangeBitDepth.HDR_10_BIT) {
            DynamicRangeBitDepth.HDR_10_BIT
        } else {
            DynamicRangeBitDepth.SDR_8_BIT
        },
        colorSpace = colorSpaceResolution.resolvedValue,
        colorRange = if (actualColorRange == ColorRange.FULL) ColorRange.FULL else ColorRange.VIDEO,
    )
    return ConstraintResolution(penalty = totalPenalty, resolvedValue = resolvedDynamicRange)
}

/** The next best color space to try if this color space is not supported. */
private fun ColorSpace.nextBestColorSpace(): ColorSpace = when (this) {
    ColorSpace.SRGB, ColorSpace.UNKNOWN -> ColorSpace.SRGB
    ColorSpace.P3_D65 -> ColorSpace.SRGB
    ColorSpace.HLG_BT2020 -> ColorSpace.P3_D65
    ColorSpace.APPLE_LOG -> ColorSpace.HLG_BT2020
    ColorSpace.APPLE_LOG_2 -> ColorSpace.APPLE_LOG
    // Dolby Vision is not supported on iOS
    ColorSpace.DOLBY_VISION -> ColorSpace.HLG_BT2020
}

/**
 * Walks the color space fallback chain until we find one the [format] supports. Returns both the penalty (number of
 * fallback steps) and the resolved color space.
 */
private fun ColorSpace.resolve(format: AVCaptureDeviceFormat): ConstraintResolution<ColorSpace> {
    val supportedColorSpaces = format.colorSpaces
    var penalty = 0
    var currentColorSpace = this
    while (currentColorSpace != ColorSpace.SRGB) {
        if (supportedColorSpaces.contains(currentColorSpace.toAVCaptureColorSpace())) break
        currentColorSpace = currentColorSpace.nextBestColorSpace()
        penalty += 1
    }
    return ConstraintResolution(penalty = penalty.toDouble(), resolvedValue = currentColorSpace)
}

/** The penalty of streaming in the [actual] color range when the [target] one is wanted. */
@Suppress("MagicNumber")
private fun colorRangePenalty(target: ColorRange, actual: ColorRange): Double = when {
    target == ColorRange.UNKNOWN || actual == ColorRange.UNKNOWN -> 3.0
    target == actual -> 0.0
    target == ColorRange.VIDEO -> 1.0
    else -> 2.0
}

/** The penalty of streaming at the [actual] bit depth when the [target] one is wanted. */
@Suppress("MagicNumber")
private fun bitDepthPenalty(target: DynamicRangeBitDepth, actual: DynamicRangeBitDepth): Double = when {
    target == DynamicRangeBitDepth.UNKNOWN || actual == DynamicRangeBitDepth.UNKNOWN -> 3.0
    target == actual -> 0.0
    target == DynamicRangeBitDepth.SDR_8_BIT -> 1.0
    else -> 2.0
}
