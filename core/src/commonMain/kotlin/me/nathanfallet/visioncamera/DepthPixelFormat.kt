package me.nathanfallet.visioncamera

/**
 * Represents the pixel format of a depth image buffer.
 *
 * `DEPTH_*` formats use actual depth sensors (infra-red, time-of-flight or LiDAR), whereas `DISPARITY_*` formats
 * come from multiple constituent physical cameras and represent pixel shift between two or more cameras.
 */
enum class DepthPixelFormat : PixelFormat {
    /** Planar 16-bit float depth. */
    DEPTH_16_BIT,

    /** Planar 32-bit float depth. */
    DEPTH_32_BIT,

    /** Interleaved 32-bit float `[x, y, z, confidence]` point cloud. */
    DEPTH_POINT_CLOUD_32_BIT,

    /** Planar 16-bit float disparity. */
    DISPARITY_16_BIT,

    /** Planar 32-bit float disparity. */
    DISPARITY_32_BIT,

    /** An unknown depth format. */
    UNKNOWN,
}
