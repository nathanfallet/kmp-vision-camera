package me.nathanfallet.visioncamera

/**
 * The [CameraDevice] represents a physical or virtual Camera Device.
 *
 * Examples:
 * - Physical: the 0.5x ultra-wide-angle Camera
 * - Virtual: the Triple-Camera consisting of the 0.5x, the 1x, and the 3x Camera
 * - TrueDepth Face ID: a virtual Camera consisting of a color, and a depth Camera
 *
 * Capabilities listed here are supported _individually_: whether a combination of features is supported is
 * negotiated by the [CameraSession] with the [Constraint] API.
 */
@Suppress("TooManyFunctions", "ComplexInterface")
interface CameraDevice : CameraDeviceOrPosition {
    // Device Metadata

    /** A unique identifier for this camera device. */
    val id: String

    /** The model identifier for this camera device. */
    val modelID: String

    /** A user friendly camera name shown by the system. */
    val localizedName: String

    /** The hardware manufacturer name for this device. */
    val manufacturer: String

    /** The camera hardware type, for example [DeviceType.WIDE_ANGLE]. */
    val type: DeviceType

    /** The position of this camera on the device. */
    val position: CameraPosition

    /**
     * If this [CameraDevice] is a virtual device (see [isVirtualDevice]), the list of all constituent physical
     * devices it is made of. For physical devices, this is an empty list.
     */
    val physicalDevices: List<CameraDevice>

    /**
     * Whether this [CameraDevice] is a virtual device, which consists of two or more physical [CameraDevice]s
     * (e.g. the "Triple-Camera" or the "FaceID Camera").
     */
    val isVirtualDevice: Boolean

    // Output Capabilities

    /** All [PixelFormat]s this [CameraDevice] can stream in. */
    val supportedPixelFormats: List<PixelFormat>

    /**
     * All resolutions this [CameraDevice] can produce for outputs of the given [outputStreamType], or an empty list
     * if that stream type can not be streamed to at all.
     */
    fun getSupportedResolutions(outputStreamType: OutputStreamType): List<Size>

    /**
     * Whether this [CameraDevice] supports streaming to the given [output].
     */
    fun supportsOutput(output: CameraOutput): Boolean

    // Capabilities

    /** Whether this [CameraDevice] supports capturing Photos in High Dynamic Range (HDR). */
    val supportsPhotoHDR: Boolean

    /** All [DynamicRange]s this [CameraDevice] supports streaming at (affects Video recordings and Preview). */
    val supportedVideoDynamicRanges: List<DynamicRange>

    /** All frame rate ranges this [CameraDevice] supports individually, in FPS. */
    val supportedFPSRanges: List<Range>

    /** Whether the given [fps] is individually supported by any of the [supportedFPSRanges]. */
    fun supportsFPS(fps: Double): Boolean

    /** Whether this [CameraDevice] supports the given stabilization mode for video streams. */
    fun supportsVideoStabilizationMode(videoStabilizationMode: TargetStabilizationMode): Boolean

    /** Whether this [CameraDevice] supports the given stabilization mode for preview streams. */
    fun supportsPreviewStabilizationMode(previewStabilizationMode: TargetStabilizationMode): Boolean

    /** Whether this [CameraDevice] supports delivering a preview image before the actual Photo is available. */
    val supportsPreviewImage: Boolean

    /** Whether this [CameraDevice] supports capturing photos with [QualityPrioritization.SPEED]. */
    val supportsSpeedQualityPrioritization: Boolean

    // Focal Length

    /** The Camera Device's nominal focal length in 35mm film format, if known. */
    val focalLength: Double?

    /** The size (ƒ number) of the lens diaphragm. */
    val lensAperture: Double

    // Continuity

    /** Whether this camera is a Continuity Camera. Always `false` on Android. */
    val isContinuityCamera: Boolean

    /** The matching Desk View camera for this Continuity Camera, if available. iOS only. */
    val companionDeskViewCamera: CameraDevice?

    /**
     * All [MediaType]s this [CameraDevice] can capture. Most Cameras return `[VIDEO]`, virtual Cameras that also
     * support Depth capture return `[VIDEO, DEPTH]`.
     */
    val mediaTypes: List<MediaType>

    // Focus

    /** Whether this [CameraDevice] supports metering auto-focus ([MeteringMode.AF]). */
    val supportsFocusMetering: Boolean

    /** Whether this [CameraDevice] supports manual focus (locking the lens position). */
    val supportsFocusLocking: Boolean

    /** Whether this [CameraDevice] supports smoothly adjusting focus. */
    val supportsSmoothAutoFocus: Boolean

    // Exposure

    /** Whether this [CameraDevice] supports metering auto-exposure ([MeteringMode.AE]). */
    val supportsExposureMetering: Boolean

    /** Whether this [CameraDevice] supports manual exposure (locking duration and ISO). */
    val supportsExposureLocking: Boolean

    /** Whether this [CameraDevice] supports biasing auto-exposure. */
    val supportsExposureBias: Boolean

    /** The minimum supported exposure bias (in EV units), or `0` if [supportsExposureBias] is `false`. */
    val minExposureBias: Double

    /** The maximum supported exposure bias (in EV units), or `0` if [supportsExposureBias] is `false`. */
    val maxExposureBias: Double

    // White Balance

    /** Whether this [CameraDevice] supports metering auto-white-balance ([MeteringMode.AWB]). */
    val supportsWhiteBalanceMetering: Boolean

    /** The maximum value a single color-channel gain can be set to, or `0` if white balance can not be locked. */
    val maxWhiteBalanceGain: Double

    /** Whether this [CameraDevice] supports manual white-balance. */
    val supportsWhiteBalanceLocking: Boolean

    // Flash

    /**
     * Whether this [CameraDevice] has a physical flash unit. Front-facing devices may still be able to use a
     * screen-flash for photo capture.
     */
    val hasFlash: Boolean

    /** Whether this [CameraDevice] supports using its flash as a continuous light ("torch"). */
    val hasTorch: Boolean

    /** Whether this [CameraDevice] supports configuring a custom torch brightness level. */
    val supportsTorchStrength: Boolean

    /** The minimum supported torch strength, or `0` if [supportsTorchStrength] is `false`. */
    val minTorchStrength: Double

    /** The maximum supported torch strength, or `0` if [supportsTorchStrength] is `false`. */
    val maxTorchStrength: Double

    // Low Light Boost

    /** Whether this [CameraDevice] supports low-light boost to improve exposure in dark scenes. */
    val supportsLowLightBoost: Boolean

    // Zoom

    /**
     * The minimum individually supported zoom value of this device. The actual minimum depends on the session
     * configuration, see [CameraController.minZoom].
     */
    val minZoom: Double

    /**
     * The maximum individually supported zoom value of this device. The actual maximum depends on the session
     * configuration, see [CameraController.maxZoom].
     */
    val maxZoom: Double

    /**
     * If this [CameraDevice] is a virtual device, the zoom factors at which it may switch to another physical
     * camera (e.g. `[1, 3]` for a 0.5x/1x/3x Triple-Camera). For physical devices, this is an empty list.
     */
    val zoomLensSwitchFactors: List<Double>

    /** Whether this [CameraDevice] supports distortion correction during Photo capture. */
    val supportsDistortionCorrection: Boolean

    /**
     * Whether the given [CameraSessionConfig] is supported by this [CameraDevice]. Configuring a [CameraSession]
     * with an unsupported config fails.
     */
    fun isSessionConfigSupported(config: CameraSessionConfig): Boolean
}
