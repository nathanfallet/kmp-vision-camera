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

    // Focus

    /**
     * Focuses the Camera pipeline to the given [point], using the [MeteringMode]s of [options] (by default, every
     * mode the [device] supports, ideally 3A: AE, AF and AWB).
     *
     * Suspends until focusing has fully settled, and throws if it timed out or was canceled. A focus operation is
     * canceled by calling [focusTo] again, or [resetFocus].
     *
     * After settling, the pipeline either keeps the point in focus as the scene changes
     * ([SceneAdaptiveness.CONTINUOUS]), or locks the values in place ([SceneAdaptiveness.LOCKED]).
     *
     * ```kotlin
     * val point = VisionCamera.createNormalizedMeteringPoint(0.5, 0.5)
     * controller.focusTo(point, FocusOptions(responsiveness = FocusResponsiveness.STEADY))
     * ```
     *
     * @throws VisionCameraException If the [device] does not support metering for one of the requested modes (see
     * [CameraDevice.supportsFocusMetering], [CameraDevice.supportsExposureMetering] and
     * [CameraDevice.supportsWhiteBalanceMetering]).
     */
    suspend fun focusTo(point: MeteringPoint, options: FocusOptions = FocusOptions())

    /**
     * Cancels any focus operation from [focusTo], resets every 3A mode back to continuous auto mode if it was locked
     * (e.g. via [setFocusLocked] or [lockCurrentFocus]), and resets the point of interest to the center.
     */
    suspend fun resetFocus()

    /**
     * Adds a listener called every time the subject area substantially changes, e.g. when the user pans away from a
     * scene previously in focus. Useful to [resetFocus] after locking focus.
     *
     * Only supported on iOS: on Android, the listener is never called.
     */
    fun addSubjectAreaChangedListener(onSubjectAreaChanged: () -> Unit): ListenerSubscription

    // Zoom animation

    /**
     * Starts animating the zoom to the given value, at the given [rate] (in doublings of the zoom factor per
     * second).
     */
    suspend fun startZoomAnimation(zoom: Double, rate: Double)

    /** Cancels any zoom animation started with [startZoomAnimation]. */
    suspend fun cancelZoomAnimation()

    // Torch strength

    /**
     * Turns the torch on at the given [strength], between [CameraDevice.minTorchStrength] and
     * [CameraDevice.maxTorchStrength]. Turn it off again with [setTorchMode].
     *
     * @throws VisionCameraException If the [device] does not support [CameraDevice.supportsTorchStrength], or the
     * [strength] is out of range.
     */
    suspend fun enableTorchWithStrength(strength: Double)

    // Manual Focus

    /** The current [FocusMode]. */
    val focusMode: FocusMode

    /**
     * The current lens position, from `0.0` (closest) to `1.0` (furthest), or `0` if the [device] does not
     * support [CameraDevice.supportsFocusLocking].
     */
    val lensPosition: Double

    /**
     * Locks the focus at the given [lensPosition], from `0.0` (closest) to `1.0` (furthest). Requires
     * [CameraDevice.supportsFocusLocking]. Only supported on iOS.
     */
    suspend fun setFocusLocked(lensPosition: Double)

    /**
     * Locks the current focus ([lensPosition]) until [resetFocus] is called. Requires
     * [CameraDevice.supportsFocusLocking]. Only supported on iOS.
     */
    suspend fun lockCurrentFocus()

    // Manual Exposure

    /** The current [ExposureMode]. */
    val exposureMode: ExposureMode

    /** The minimum exposure duration for [setExposureLocked], in seconds, or `0` if not supported. */
    val minExposureDuration: Double

    /** The maximum exposure duration for [setExposureLocked], in seconds, or `0` if not supported. */
    val maxExposureDuration: Double

    /** The minimum ISO for [setExposureLocked], or `0` if not supported. */
    val minISO: Double

    /** The maximum ISO for [setExposureLocked], or `0` if not supported. */
    val maxISO: Double

    /** The current exposure duration, in seconds, or `0` if the [device] does not support exposure locking. */
    val exposureDuration: Double

    /** The current ISO, or `0` if the [device] does not support exposure locking. */
    val iso: Double

    /**
     * Locks the exposure at the given [duration] (in seconds, between [minExposureDuration] and
     * [maxExposureDuration]) and [iso] (between [minISO] and [maxISO]). Requires
     * [CameraDevice.supportsExposureLocking]. Only supported on iOS.
     */
    suspend fun setExposureLocked(duration: Double, iso: Double)

    /**
     * Locks the current exposure ([exposureDuration] and [iso]) until [resetFocus] is called. Requires
     * [CameraDevice.supportsExposureLocking]. Only supported on iOS.
     */
    suspend fun lockCurrentExposure()

    // Manual White Balance

    /** The current [WhiteBalanceMode]. */
    val whiteBalanceMode: WhiteBalanceMode

    /**
     * The current white balance gains, or `(0, 0, 0)` if the [device] does not support
     * [CameraDevice.supportsWhiteBalanceLocking].
     */
    val whiteBalanceGains: WhiteBalanceGains

    /** Converts the given temperature and tint values to [WhiteBalanceGains]. Only supported on iOS. */
    fun convertWhiteBalanceTemperatureAndTintValues(
        whiteBalanceTemperatureAndTint: WhiteBalanceTemperatureAndTint,
    ): WhiteBalanceGains

    /**
     * Locks the white balance at the given [whiteBalanceGains], each channel between `1` and
     * [CameraDevice.maxWhiteBalanceGain]. Requires [CameraDevice.supportsWhiteBalanceLocking]. Only supported on
     * iOS.
     */
    suspend fun setWhiteBalanceLocked(whiteBalanceGains: WhiteBalanceGains)

    /**
     * Locks the current white balance ([whiteBalanceGains]) until [resetFocus] is called. Requires
     * [CameraDevice.supportsWhiteBalanceLocking]. Only supported on iOS.
     */
    suspend fun lockCurrentWhiteBalance()
}
