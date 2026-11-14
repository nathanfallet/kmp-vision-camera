package me.nathanfallet.visioncamera

/**
 * Represents any kind of pixel format for a media sample:
 * - a [VideoPixelFormat]: a video-based format,
 * - a [DepthPixelFormat]: a depth/disparity-based format,
 * - [PixelFormat.Private]: an implementation-defined private format, possibly only allowing GPU access.
 */
sealed interface PixelFormat {
    /**
     * An implementation-defined private Format, possibly only allowing GPU access.
     */
    data object Private : PixelFormat
}
