package me.nathanfallet.visioncamera

/**
 * Represents the pixel format of a video image buffer. Video formats are either **YUV** or **RGB**.
 * The most commonly used format is [YUV_420_8_BIT_VIDEO].
 */
enum class VideoPixelFormat : PixelFormat {
    /** YUV 4:2:0, 8-bit, video range, planar. */
    YUV_420_8_BIT_VIDEO,

    /** YUV 4:2:0, 8-bit, full range, planar. */
    YUV_420_8_BIT_FULL,

    /** YUV 4:2:0, 10-bit, video range, planar. */
    YUV_420_10_BIT_VIDEO,

    /** YUV 4:2:0, 10-bit, full range, planar. */
    YUV_420_10_BIT_FULL,

    /** YUV 4:2:2, 8-bit, video range, planar. */
    YUV_422_8_BIT_VIDEO,

    /** YUV 4:2:2, 8-bit, full range, planar. */
    YUV_422_8_BIT_FULL,

    /** YUV 4:2:2, 10-bit, video range, planar. */
    YUV_422_10_BIT_VIDEO,

    /** YUV 4:2:2, 10-bit, full range, planar. */
    YUV_422_10_BIT_FULL,

    /** YUV 4:4:4, 8-bit, video range, planar. */
    YUV_444_8_BIT_VIDEO,

    /** YUV 4:4:4, 8-bit, full range, planar. */
    YUV_444_8_BIT_FULL,

    /** RGB, 8-bit, interleaved BGRA. */
    RGB_BGRA_8_BIT,

    /** RGB, 8-bit, interleaved RGBA. */
    RGB_RGBA_8_BIT,

    /** RGB, 8-bit, interleaved RGB/RGBX. */
    RGB_RGB_8_BIT,

    /** RAW Bayer, 12-bit, packed96. */
    RAW_BAYER_PACKED96_12_BIT,

    /** RAW Bayer, 16-bit, unpacked. */
    RAW_BAYER_UNPACKED_16_BIT,

    /** An unknown video format. */
    UNKNOWN,
}

/**
 * Represents a desired pixel format for a video pipeline, e.g. the format a frame output streams frames in.
 */
enum class TargetVideoPixelFormat {
    /** The session's native pixel format, which requires zero conversion. */
    NATIVE,

    /** The YUV format closest to the Camera's native format, often [VideoPixelFormat.YUV_420_8_BIT_FULL]. */
    YUV,

    /** An RGB format, often [VideoPixelFormat.RGB_BGRA_8_BIT]. */
    RGB,
}
