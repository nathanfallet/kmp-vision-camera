package me.nathanfallet.visioncamera

/**
 * A resolved session-level configuration for a [CameraSession], the result of negotiating [Constraint]s.
 *
 * @see CameraFactory.resolveConstraints
 * @see CameraDevice.isSessionConfigSupported
 */
interface CameraSessionConfig {
    /** The selected FPS, or `null` if no specific FPS value has been selected. */
    val selectedFPS: Double?

    /** The selected stabilization mode for Video Streams, or `null` if none has been configured. */
    val selectedVideoStabilizationMode: TargetStabilizationMode?

    /** The selected stabilization mode for Preview Streams, or `null` if none has been configured. */
    val selectedPreviewStabilizationMode: TargetStabilizationMode?

    /** The selected dynamic range for Video Streams, or `null` if none has been configured. */
    val selectedVideoDynamicRange: TargetDynamicRange?

    /** Whether Photo HDR is enabled. */
    val isPhotoHDREnabled: Boolean

    /**
     * The [PixelFormat] this config is natively streaming in. If it matches the pixel format requested by a
     * streaming output, no conversion takes place.
     */
    val nativePixelFormat: PixelFormat

    /** The [AutoFocusSystem] used by this config. */
    val autoFocusSystem: AutoFocusSystem

    /** Whether this config is streaming in a binned format. */
    val isBinned: Boolean
}
