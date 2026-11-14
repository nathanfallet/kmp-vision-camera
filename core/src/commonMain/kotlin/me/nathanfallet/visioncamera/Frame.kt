package me.nathanfallet.visioncamera

/**
 * A single Camera Frame, backed by a native buffer (an `ImageProxy` on Android, a `CMSampleBuffer` on iOS).
 *
 * **Lifetime contract**: a [Frame] is only valid while [isValid] is `true`. The Camera pipeline has a small pool of
 * buffers, so every [Frame] must be [dispose]d as soon as possible, otherwise the pipeline stalls and frames get
 * dropped ([FrameDroppedReason.OUT_OF_BUFFERS]). Accessing a disposed [Frame] throws. A [Frame] must not be shared
 * across coroutines or stored beyond the processing callback.
 *
 * [Frame] is [AutoCloseable], so it can be used with `use { }`:
 * ```kotlin
 * frame.use {
 *     // processing...
 * }
 * ```
 */
interface Frame : AutoCloseable {
    /** The presentation timestamp of this Frame, in nanoseconds. */
    val timestamp: Long

    /** Whether this Frame is still valid, i.e. it has not been disposed yet. */
    val isValid: Boolean

    /** The width of this Frame, in pixels. */
    val width: Int

    /** The height of this Frame, in pixels. */
    val height: Int

    /** The number of bytes per row of this Frame's first plane. */
    val bytesPerRow: Int

    /** The [PixelFormat] of this Frame. */
    val pixelFormat: PixelFormat

    /** The orientation of this Frame's content, relative to the output orientation. */
    val orientation: CameraOrientation

    /** Whether this Frame is mirrored. */
    val isMirrored: Boolean

    /** Whether this Frame is planar (e.g. YUV), or interleaved (e.g. RGB). */
    val isPlanar: Boolean

    /** Whether this Frame can be accessed on the CPU. */
    val hasPixelBuffer: Boolean

    /** Whether this Frame can be accessed as a [NativeBuffer]. */
    val hasNativeBuffer: Boolean

    /**
     * Gets a [NativeBuffer] to this Frame's native platform buffer, for zero-copy GPU or ML processing.
     * The returned buffer must be [NativeBuffer.release]d before this Frame is disposed.
     */
    fun getNativeBuffer(): NativeBuffer

    /** The camera intrinsic matrix (3x3, row-major) of this Frame, if available. */
    val cameraIntrinsicMatrix: List<Double>?

    /** Converts a point in the Camera's normalized coordinate system to this Frame's coordinate system. */
    fun convertCameraPointToFramePoint(cameraPoint: Point): Point

    /** Converts a point in this Frame's coordinate system to the Camera's normalized coordinate system. */
    fun convertFramePointToCameraPoint(framePoint: Point): Point

    /**
     * Disposes this Frame, giving its buffer back to the Camera pipeline. Calling it more than once is a no-op.
     */
    fun dispose()

    /**
     * Same as [dispose], to use a [Frame] with `use { }`.
     */
    override fun close() {
        dispose()
    }
}
