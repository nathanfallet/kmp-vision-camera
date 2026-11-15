package me.nathanfallet.visioncamera

/**
 * The [CameraFactory] allows creating various other Camera related objects.
 *
 * Use the [VisionCamera] instance:
 * ```kotlin
 * if (VisionCamera.cameraPermissionStatus != PermissionStatus.AUTHORIZED) {
 *     VisionCamera.requestCameraPermission()
 * }
 * val session = VisionCamera.createCameraSession(enableMultiCam = false)
 * ```
 */
interface CameraFactory {
    /** The current Camera [PermissionStatus]. */
    val cameraPermissionStatus: PermissionStatus

    /** The current Microphone [PermissionStatus]. */
    val microphonePermissionStatus: PermissionStatus

    /**
     * Request Camera permission.
     *
     * @return `true` if Camera permission has now been granted, `false` otherwise.
     */
    suspend fun requestCameraPermission(): Boolean

    /**
     * Request Microphone permission.
     *
     * @return `true` if Microphone permission has now been granted, `false` otherwise.
     */
    suspend fun requestMicrophonePermission(): Boolean

    /**
     * Whether the platform supports creating multi-cam [CameraSession]s. Older phones may not support this.
     */
    val supportsMultiCamSessions: Boolean

    /**
     * Creates a new [CameraSession].
     *
     * If [enableMultiCam] is `true`, the session supports attaching multiple [CameraDevice]s as inputs at the same
     * time. Otherwise, it only supports a single [CameraDevice] input at a time.
     *
     * @throws VisionCameraException If [enableMultiCam] is `true`, but [supportsMultiCamSessions] is `false`.
     */
    suspend fun createCameraSession(enableMultiCam: Boolean): CameraSession

    /**
     * Resolves the given [constraints] to a fully validated [CameraSessionConfig] respecting the given [device]'s
     * capabilities negotiated with the given [outputConfigurations].
     *
     * The [constraints] are ranked by priority in the order they are passed, descending.
     */
    suspend fun resolveConstraints(
        device: CameraDevice,
        outputConfigurations: List<CameraOutputConfiguration>,
        constraints: List<Constraint>,
        requiresMultiCam: Boolean = false,
    ): CameraSessionConfig

    /**
     * Create a new [CameraDeviceFactory].
     */
    suspend fun createDeviceFactory(): CameraDeviceFactory

    /**
     * Create a new [CameraPreviewOutput].
     */
    fun createPreviewOutput(): CameraPreviewOutput

    /**
     * Create a new [CameraPhotoOutput] with the given [options].
     */
    fun createPhotoOutput(options: PhotoOutputOptions = PhotoOutputOptions()): CameraPhotoOutput

    /**
     * Create a new [CameraVideoOutput] with the given [options].
     */
    fun createVideoOutput(options: VideoOutputOptions = VideoOutputOptions()): CameraVideoOutput

    /**
     * Create a new [CameraFrameOutput] with the given [options], to stream [Frame]s (Frame Processing).
     */
    fun createFrameOutput(options: FrameOutputOptions = FrameOutputOptions()): CameraFrameOutput

    /**
     * Create a new [CameraDepthFrameOutput] with the given [options], to stream [Depth] frames.
     */
    fun createDepthFrameOutput(options: DepthFrameOutputOptions = DepthFrameOutputOptions()): CameraDepthFrameOutput

    /**
     * Create a new [CameraObjectOutput] with the given [options], to scan codes, faces and bodies.
     *
     * @throws VisionCameraException On Android, where it is not available.
     */
    fun createObjectOutput(options: ObjectOutputOptions): CameraObjectOutput

    /**
     * Create a new [CameraOutputSynchronizer] synchronizing the given [outputs].
     *
     * @throws VisionCameraException On Android, where it is not available.
     */
    fun createOutputSynchronizer(outputs: List<CameraOutput>): CameraOutputSynchronizer

    /**
     * Create a new [ZoomGestureController], to attach to a `PreviewView`.
     */
    fun createZoomGestureController(): ZoomGestureController

    /**
     * Create a new [TapToFocusGestureController], to attach to a `PreviewView`.
     */
    fun createTapToFocusGestureController(): TapToFocusGestureController

    /**
     * Create a new [OrientationManager] tracking the given [orientationSource].
     */
    fun createOrientationManager(orientationSource: OrientationSource): OrientationManager

    /**
     * Create a new [FrameRenderer], to render [Frame]s into a `FrameRendererView`.
     */
    fun createFrameRenderer(): FrameRenderer

    /**
     * Create a [MeteringPoint] from normalized coordinates, from `0.0` to `1.0`. You are responsible for handling
     * orientation, cropping and scaling; to create a point from a tap on a `PreviewView`, use its
     * `createMeteringPoint` instead.
     *
     * @param x The X coordinate, from `0.0` to `1.0`.
     * @param y The Y coordinate, from `0.0` to `1.0`.
     * @param size The size of the point, or `null` for the platform default.
     */
    fun createNormalizedMeteringPoint(x: Double, y: Double, size: Double? = null): MeteringPoint
}
