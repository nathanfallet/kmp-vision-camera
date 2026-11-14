package me.nathanfallet.visioncamera

/**
 * Represents the color space of a [DynamicRange].
 */
enum class ColorSpace {
    /** sRGB. */
    SRGB,

    /** Dolby Vision. */
    DOLBY_VISION,

    /** Display P3 D65. */
    P3_D65,

    /** HLG BT.2020. */
    HLG_BT2020,

    /** Apple Log. */
    APPLE_LOG,

    /** Apple Log 2. */
    APPLE_LOG_2,

    /** Unknown color space. It can not be used as a target. */
    UNKNOWN,
}

/**
 * Represents the bit-depth of a [DynamicRange].
 */
enum class DynamicRangeBitDepth {
    /** 8 bits per channel, often called "SDR". */
    SDR_8_BIT,

    /** 10 bits per channel, often called "HDR". */
    HDR_10_BIT,

    /** Unknown bit-depth. It can not be used as a target. */
    UNKNOWN,
}

/**
 * Represents the range of YUV color components.
 */
enum class ColorRange {
    /** Limited Color Range, Y ranges from 16–235 and UV ranges from 16–240. */
    VIDEO,

    /** Full Color Range, both Y and UV range from 0-255. */
    FULL,

    /** Unknown color range. It can not be used as a target. */
    UNKNOWN,
}

/**
 * Represents a Dynamic Range, often used for recording HDR videos.
 *
 * @see CameraDevice.supportedVideoDynamicRanges
 * @see VideoDynamicRangeConstraint
 */
data class DynamicRange(
    /** The bit-depth for the dynamic range, often 8-bit or 10-bit. */
    val bitDepth: DynamicRangeBitDepth,
    /** The color-space for the dynamic range. */
    val colorSpace: ColorSpace,
    /** The range of YUV color components. */
    val colorRange: ColorRange,
)

/**
 * Represents a target [DynamicRange], for example to be used with the Constraints API.
 * None of its components can be `UNKNOWN`.
 *
 * @see VideoDynamicRangeConstraint
 * @see CommonDynamicRanges
 */
data class TargetDynamicRange(
    /** The target bit-depth. */
    val bitDepth: DynamicRangeBitDepth,
    /** The target color-space. */
    val colorSpace: ColorSpace,
    /** The target range of YUV color components. */
    val colorRange: ColorRange,
) {
    init {
        require(bitDepth != DynamicRangeBitDepth.UNKNOWN) { "bitDepth can not be UNKNOWN" }
        require(colorSpace != ColorSpace.UNKNOWN) { "colorSpace can not be UNKNOWN" }
        require(colorRange != ColorRange.UNKNOWN) { "colorRange can not be UNKNOWN" }
    }
}
