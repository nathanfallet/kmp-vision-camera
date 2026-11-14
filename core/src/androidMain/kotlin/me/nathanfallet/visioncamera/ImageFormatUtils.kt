package me.nathanfallet.visioncamera

import android.graphics.ImageFormat

/**
 * Classifies Android [ImageFormat]s.
 */
internal object ImageFormatUtils {
    private val allVideoFormats = intArrayOf(
        // YUV
        ImageFormat.PRIVATE,
        ImageFormat.YUV_420_888,
        ImageFormat.YUV_422_888,
        ImageFormat.YUV_444_888,
        ImageFormat.YCBCR_P010,
        ImageFormat.YCBCR_P210,
        // RGB
        ImageFormat.FLEX_RGB_888,
        ImageFormat.FLEX_RGBA_8888,
    )
    private val allDepthFormats = intArrayOf(
        ImageFormat.DEPTH16,
        ImageFormat.DEPTH_JPEG,
        ImageFormat.DEPTH_POINT_CLOUD,
    )
    private val allPhotoFormats = intArrayOf(
        ImageFormat.JPEG,
        ImageFormat.JPEG_R,
        ImageFormat.DEPTH_JPEG,
        ImageFormat.RAW_SENSOR,
        ImageFormat.HEIC,
        ImageFormat.HEIC_ULTRAHDR,
    )

    fun isVideoFormat(format: Int): Boolean = format in allVideoFormats

    fun isDepthFormat(format: Int): Boolean = format in allDepthFormats

    fun isPhotoFormat(format: Int): Boolean = format in allPhotoFormats
}

/**
 * The YUV range of a pixel format.
 */
internal enum class PixelRange {
    VIDEO,
    FULL,
    UNKNOWN,
}

/**
 * Converts an Android [ImageFormat] (or `android.graphics.PixelFormat`) to a [PixelFormat].
 */
@Suppress("CyclomaticComplexMethod")
internal fun pixelFormatFromImageFormat(imageFormat: Int, range: PixelRange): PixelFormat {
    val isVideoRange = range == PixelRange.VIDEO
    return when (imageFormat) {
        ImageFormat.FLEX_RGB_888 -> VideoPixelFormat.RGB_RGB_8_BIT
        ImageFormat.FLEX_RGBA_8888 -> VideoPixelFormat.RGB_RGBA_8_BIT
        android.graphics.PixelFormat.RGBA_8888 -> VideoPixelFormat.RGB_RGBA_8_BIT
        android.graphics.PixelFormat.RGBX_8888 -> VideoPixelFormat.RGB_RGB_8_BIT
        android.graphics.PixelFormat.RGB_888 -> VideoPixelFormat.RGB_RGB_8_BIT
        ImageFormat.RAW_SENSOR -> VideoPixelFormat.RAW_BAYER_UNPACKED_16_BIT
        ImageFormat.RAW12 -> VideoPixelFormat.RAW_BAYER_PACKED96_12_BIT
        ImageFormat.DEPTH16 -> DepthPixelFormat.DEPTH_16_BIT
        ImageFormat.DEPTH_POINT_CLOUD -> DepthPixelFormat.DEPTH_POINT_CLOUD_32_BIT
        ImageFormat.YUV_420_888 ->
            if (isVideoRange) VideoPixelFormat.YUV_420_8_BIT_VIDEO else VideoPixelFormat.YUV_420_8_BIT_FULL
        ImageFormat.YUV_422_888 ->
            if (isVideoRange) VideoPixelFormat.YUV_422_8_BIT_VIDEO else VideoPixelFormat.YUV_422_8_BIT_FULL
        ImageFormat.YUV_444_888 ->
            if (isVideoRange) VideoPixelFormat.YUV_444_8_BIT_VIDEO else VideoPixelFormat.YUV_444_8_BIT_FULL
        ImageFormat.YCBCR_P010 ->
            if (isVideoRange) VideoPixelFormat.YUV_420_10_BIT_VIDEO else VideoPixelFormat.YUV_420_10_BIT_FULL
        ImageFormat.YCBCR_P210 ->
            if (isVideoRange) VideoPixelFormat.YUV_422_10_BIT_VIDEO else VideoPixelFormat.YUV_422_10_BIT_FULL
        ImageFormat.PRIVATE -> PixelFormat.Private
        else -> VideoPixelFormat.UNKNOWN
    }
}
