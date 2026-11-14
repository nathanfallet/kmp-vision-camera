package me.nathanfallet.visioncamera

/**
 * Represents the type of an output stream, used to query available resolutions via
 * [CameraDevice.getSupportedResolutions].
 */
enum class OutputStreamType {
    /** Photo capture. */
    PHOTO,

    /** Video recording. */
    VIDEO,

    /** Frame streaming (preview, frame processing). */
    STREAM,

    /** Depth data delivered with photos. */
    DEPTH_PHOTO,

    /** Depth data streaming. */
    DEPTH_STREAM,
}
