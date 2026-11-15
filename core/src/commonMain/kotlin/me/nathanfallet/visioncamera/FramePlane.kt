package me.nathanfallet.visioncamera

/**
 * A single plane of a **planar** [Frame], e.g. the **Y** or the **UV** plane of a YUV Frame.
 *
 * @see Frame.getPlanes
 */
interface FramePlane {
    /** The width of this plane, in pixels, or `0` if it is not valid anymore. */
    val width: Int

    /** The height of this plane, in pixels, or `0` if it is not valid anymore. */
    val height: Int

    /** The number of bytes per row of this plane's pixel data. */
    val bytesPerRow: Int

    /** Whether this plane (and its parent [Frame]) is still valid, i.e. the [Frame] has not been disposed yet. */
    val isValid: Boolean

    /**
     * Gets this plane's pixel data, as a copy.
     *
     * @throws VisionCameraException If this plane is not valid anymore.
     */
    fun getPixelBuffer(): ByteArray
}
