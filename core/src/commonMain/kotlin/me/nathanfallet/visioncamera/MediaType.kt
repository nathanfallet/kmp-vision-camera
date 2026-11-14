package me.nathanfallet.visioncamera

/**
 * Represents the type of a media sample.
 */
enum class MediaType {
    /** A pixel-based format suitable for recordings, each pixel contains a color. */
    VIDEO,

    /** A pixel-based depth or disparity map, each pixel is a physical distance. */
    DEPTH,

    /** An object-based format, like QR codes. */
    METADATA,

    /** An unknown media format. */
    OTHER,
}
