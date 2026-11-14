package me.nathanfallet.visioncamera

/**
 * Commonly used [TargetDynamicRange]s.
 */
object CommonDynamicRanges {
    /** Any SDR range (8-bit sRGB, full range). */
    val ANY_SDR: TargetDynamicRange = TargetDynamicRange(
        bitDepth = DynamicRangeBitDepth.SDR_8_BIT,
        colorSpace = ColorSpace.SRGB,
        colorRange = ColorRange.FULL,
    )

    /** Any HDR range (10-bit HLG BT.2020, full range). */
    val ANY_HDR: TargetDynamicRange = TargetDynamicRange(
        bitDepth = DynamicRangeBitDepth.HDR_10_BIT,
        colorSpace = ColorSpace.HLG_BT2020,
        colorRange = ColorRange.FULL,
    )
}
