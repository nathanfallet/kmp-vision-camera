package me.nathanfallet.visioncamera

/**
 * Represents a video codec for encoded video data.
 */
enum class VideoCodec {
    /** H.264 / AVC, the most widely supported video format. */
    H264,

    /** H.265 / HEVC, higher efficiency than H.264. */
    H265,

    /** H.265 / HEVC with alpha. */
    H265_WITH_ALPHA,

    /** ProRes 422 Proxy. */
    PRO_RES_422_PROXY,

    /** ProRes 422 LT. */
    PRO_RES_422_LT,

    /** ProRes 422. */
    PRO_RES_422,

    /** ProRes 422 HQ. */
    PRO_RES_422_HQ,

    /** ProRes 4444. */
    PRO_RES_4444,

    /** ProRes 4444 XQ. */
    PRO_RES_4444_XQ,

    /** ProRes RAW. */
    PRO_RES_RAW,

    /** ProRes RAW HQ. */
    PRO_RES_RAW_HQ,

    /** Legacy motion JPEG. */
    JPEG,

    /** An unknown codec, it can not be used. */
    UNKNOWN,
}
