package me.nathanfallet.visioncamera

/** The measurement accuracy of a [Depth] frame. */
enum class DepthDataAccuracy {
    /** The depth values represent relative distances. */
    RELATIVE,

    /** The depth values represent absolute distances. */
    ABSOLUTE,

    /** The accuracy is unknown or unavailable. */
    UNKNOWN,
}

/** The quality classification of a [Depth] frame. */
enum class DepthDataQuality {
    /** Lower quality depth data. */
    LOW,

    /** High quality depth data. */
    HIGH,

    /** The quality is unknown or unavailable. */
    UNKNOWN,
}

/**
 * A Depth frame, captured by a [CameraDepthFrameOutput] or embedded in a [Photo] ([Photo.depth]).
 *
 * Like a [Frame], a [Depth] must be disposed as soon as possible, otherwise the producing pipeline might stall. It is
 * [AutoCloseable], so it can be used with `use { }`.
 */
interface Depth : FrameOrDepth, AutoCloseable {
    /**
     * The rotation of this Depth relative to the output orientation. Depth data is not rotated to
     * [CameraOrientation.UP], because physically rotating buffers is expensive: rotate it yourself, or with [rotate].
     */
    val orientation: CameraOrientation

    /** Whether this Depth frame is mirrored along the vertical axis. */
    val isMirrored: Boolean

    /** The presentation timestamp of this Depth frame, in nanoseconds. */
    val timestamp: Long

    /** The width of this Depth frame, or `0` if it is not valid anymore. */
    val width: Int

    /** The height of this Depth frame, or `0` if it is not valid anymore. */
    val height: Int

    /** The number of bytes per row of the underlying pixel buffer. */
    val bytesPerRow: Int

    /** The [DepthPixelFormat] of this Depth frame's data. */
    val pixelFormat: DepthPixelFormat

    /** Whether this Depth is still valid, i.e. it has not been disposed yet. */
    val isValid: Boolean

    /** Whether the depth map has been filtered (smoothed) by the platform. */
    val isDepthDataFiltered: Boolean

    /** The measurement accuracy of this Depth. */
    val depthDataAccuracy: DepthDataAccuracy

    /** The quality classification of this Depth. */
    val depthDataQuality: DepthDataQuality

    /** The [DepthPixelFormat]s this Depth can be [convert]ed to. May be empty if conversion is not supported. */
    val availableDepthPixelFormats: List<DepthPixelFormat>

    /** The [CameraCalibrationData] of this Depth, if available. iOS only. */
    val cameraCalibrationData: CameraCalibrationData?

    /**
     * Gets this Depth's data, as a copy.
     *
     * @throws VisionCameraException If this Depth is not valid anymore.
     */
    fun getDepthData(): ByteArray

    /**
     * Gets a [NativeBuffer] to this Depth's native platform buffer. The returned buffer must be
     * [NativeBuffer.release]d, otherwise the pipeline might stall.
     */
    fun getNativeBuffer(): NativeBuffer

    /**
     * Returns a new Depth frame, rotated to the given [orientation] and optionally mirrored. The result must be
     * disposed too. This runs synchronously, on the calling thread.
     */
    fun rotate(orientation: CameraOrientation, isMirrored: Boolean): Depth

    /**
     * Returns a new Depth frame converted to the given [pixelFormat], one of [availableDepthPixelFormats]. The result
     * must be disposed too. This runs synchronously, on the calling thread.
     */
    fun convert(pixelFormat: DepthPixelFormat): Depth

    /** Converts a point in camera sensor coordinates to this Depth's coordinates. */
    fun convertCameraPointToDepthPoint(cameraPoint: Point): Point

    /** Converts a point in this Depth's coordinates to camera sensor coordinates. */
    fun convertDepthPointToCameraPoint(depthPoint: Point): Point

    /**
     * Converts this Depth frame to a [Frame] containing the depth or disparity data in the current [pixelFormat]. The
     * resulting [Frame] must be disposed too.
     */
    fun toFrame(): Frame

    /** Disposes this Depth, freeing its memory. Calling it more than once is a no-op. */
    fun dispose()

    /** Same as [dispose], to use a [Depth] with `use { }`. */
    override fun close() {
        dispose()
    }
}
