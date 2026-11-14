package me.nathanfallet.visioncamera

/**
 * Configuration options of a [CameraController].
 */
data class CameraControllerConfiguration(
    /** Enables low-light boost, if [CameraDevice.supportsLowLightBoost]. */
    val enableLowLightBoost: Boolean? = null,
    /** Enables smooth auto-focus, if [CameraDevice.supportsSmoothAutoFocus]. */
    val enableSmoothAutoFocus: Boolean? = null,
    /** Enables distortion correction, if [CameraDevice.supportsDistortionCorrection]. */
    val enableDistortionCorrection: Boolean? = null,
)

/**
 * A [CameraController] controls a [CameraDevice] connected to a [CameraSession]: zoom, torch, exposure, focus…
 *
 * One [CameraController] is returned per [CameraSessionConnection] by [CameraSession.configure].
 */
interface CameraController {
    /** The [CameraDevice] this controller controls. */
    val device: CameraDevice

    // Device State

    /** Whether the [device] is currently connected and usable. */
    val isConnected: Boolean

    /** Whether the [device] is currently suspended (e.g. a lens cover is closed). */
    val isSuspended: Boolean

    /** Whether the [device] is currently used by another app. */
    val isUsedByAnotherApp: Boolean

    /** Whether low-light boost is currently enabled. */
    val isLowLightBoostEnabled: Boolean

    /** Whether smooth auto-focus is currently enabled. */
    val isSmoothAutoFocusEnabled: Boolean

    /** Whether distortion correction is currently enabled. */
    val isDistortionCorrectionEnabled: Boolean

    /**
     * Configures this controller with the given [config]. `null` values are left unchanged.
     */
    suspend fun configure(config: CameraControllerConfiguration)

    // Zoom

    /** The current zoom value. */
    val zoom: Double

    /** The minimum zoom value supported in the current session configuration. */
    val minZoom: Double

    /** The maximum zoom value supported in the current session configuration. */
    val maxZoom: Double

    /**
     * The zoom factor to display to the user, which may differ from [zoom] on virtual devices (e.g. `0.5x` on a
     * Triple-Camera whose [minZoom] is `1`).
     */
    val displayableZoomFactor: Double

    /**
     * Sets the zoom to the given value, clamped between [minZoom] and [maxZoom].
     */
    suspend fun setZoom(zoom: Double)

    // Flash

    /** The current torch strength. */
    val torchStrength: Double

    /** The current [TorchMode]. */
    val torchMode: TorchMode

    /**
     * Sets the torch to the given [mode], at the system default level. Requires [CameraDevice.hasTorch].
     */
    suspend fun setTorchMode(mode: TorchMode)

    // Exposure

    /** The current exposure bias, in EV units. */
    val exposureBias: Double

    /**
     * Biases the auto-exposure by the given value in EV units, clamped between [CameraDevice.minExposureBias] and
     * [CameraDevice.maxExposureBias]. Requires [CameraDevice.supportsExposureBias].
     */
    suspend fun setExposureBias(exposure: Double)
}
