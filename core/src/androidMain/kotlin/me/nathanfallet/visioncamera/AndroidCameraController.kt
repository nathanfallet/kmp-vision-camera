package me.nathanfallet.visioncamera

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.LowLightBoostState
import androidx.camera.core.TorchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * The Android [CameraController], controlling a bound CameraX [Camera].
 */
@Suppress("TooManyFunctions")
internal class AndroidCameraController(
    /** The bound CameraX [Camera]. */
    val camera: Camera,
) : CameraController {
    override val device: CameraDevice = AndroidCameraDevice(camera.cameraInfo)
    private val cameraState: CameraState? get() = camera.cameraInfo.cameraState.value
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The running zoom animation, only accessed on the main thread. */
    private var zoomAnimator: ValueAnimator? = null

    override val isConnected: Boolean get() = cameraState?.type == CameraState.Type.OPEN
    override val isSuspended: Boolean
        get() = cameraState?.error?.code == CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED
    override val isUsedByAnotherApp: Boolean get() = cameraState?.error?.code == CameraState.ERROR_CAMERA_IN_USE
    override val isLowLightBoostEnabled: Boolean
        get() = camera.cameraInfo.lowLightBoostState.value != LowLightBoostState.OFF

    // CameraX does not support changing those yet.
    override val isSmoothAutoFocusEnabled: Boolean get() = false
    override val isDistortionCorrectionEnabled: Boolean get() = false

    override suspend fun configure(config: CameraControllerConfiguration) = withContext(Dispatchers.Main) {
        config.enableLowLightBoost?.let { camera.cameraControl.enableLowLightBoostAsync(it).await() }
        // enableSmoothAutoFocus and enableDistortionCorrection are not supported by CameraX yet.
        Unit
    }

    // Zoom

    override val zoom: Double get() = camera.cameraInfo.zoomState.value?.zoomRatio?.toDouble() ?: 1.0
    override val minZoom: Double get() = camera.cameraInfo.zoomState.value?.minZoomRatio?.toDouble() ?: 1.0
    override val maxZoom: Double get() = camera.cameraInfo.zoomState.value?.maxZoomRatio?.toDouble() ?: 1.0
    override val displayableZoomFactor: Double get() = zoom * camera.cameraInfo.intrinsicZoomRatio

    override suspend fun setZoom(zoom: Double) = withContext(Dispatchers.Main) {
        val zoomState = camera.cameraInfo.zoomState.value
        if (zoomState != null && (zoom < zoomState.minZoomRatio || zoom > zoomState.maxZoomRatio)) {
            throw VisionCameraException(
                "`zoom` is out of range! Expected value within " +
                    "${zoomState.minZoomRatio}...${zoomState.maxZoomRatio}, received $zoom.",
            )
        }
        camera.cameraControl.setZoomRatio(zoom.toFloat()).await()
        Unit
    }

    override suspend fun startZoomAnimation(zoom: Double, rate: Double) = withContext(Dispatchers.Main) {
        if (rate <= 0.0) throw VisionCameraException("Zoom animation `rate` must be greater than 0! (Received: $rate)")
        zoomAnimator?.cancel()

        val from = this@AndroidCameraController.zoom
        val to = zoom.coerceIn(minZoom, maxZoom)
        // We are already exactly on our target value
        if (from == to) return@withContext
        suspendCancellableCoroutine<Unit> { continuation ->
            val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
            animator.duration = zoomAnimationDurationMillis(from, to, rate)
            animator.addUpdateListener { camera.cameraControl.setZoomRatio(it.animatedValue as Float) }
            animator.addListener(
                object : AnimatorListenerAdapter() {
                    private var isCanceled = false

                    override fun onAnimationCancel(animation: Animator) {
                        isCanceled = true
                        if (continuation.isActive) {
                            continuation.resumeWithException(VisionCameraException("The Zoom animation was canceled!"))
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (zoomAnimator === animation) zoomAnimator = null
                        if (!isCanceled && continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
            zoomAnimator = animator
            continuation.invokeOnCancellation { mainHandler.post { animator.cancel() } }
            animator.start()
        }
    }

    override suspend fun cancelZoomAnimation() = withContext(Dispatchers.Main) {
        zoomAnimator?.cancel()
        zoomAnimator = null
    }

    // Flash

    override val torchStrength: Double get() = camera.cameraInfo.torchStrengthLevel.value?.toDouble() ?: 0.0
    override val torchMode: TorchMode
        get() = torchModeFromTorchState(camera.cameraInfo.torchState.value ?: TorchState.OFF)

    override suspend fun setTorchMode(mode: TorchMode) = withContext(Dispatchers.Main) {
        camera.cameraControl.enableTorch(mode == TorchMode.ON).await()
        Unit
    }

    override suspend fun enableTorchWithStrength(strength: Double) = withContext(Dispatchers.Main) {
        if (!device.supportsTorchStrength) {
            throw VisionCameraException("This device does not support setting a custom torch strength!")
        }
        if (strength !in device.minTorchStrength..device.maxTorchStrength) {
            throw VisionCameraException(
                "Torch `strength` is out of range! Expected value within " +
                    "${device.minTorchStrength}...${device.maxTorchStrength}, received $strength.",
            )
        }
        camera.cameraControl.setTorchStrengthLevel(strength.toInt()).await()
        camera.cameraControl.enableTorch(true).await()
        Unit
    }

    // Exposure

    override val exposureBias: Double get() = camera.cameraInfo.exposureState.exposureCompensationIndex.toDouble()

    override suspend fun setExposureBias(exposure: Double) = withContext(Dispatchers.Main) {
        camera.cameraControl.setExposureCompensationIndex(exposure.toInt()).await()
        Unit
    }

    // Focus

    override suspend fun focusTo(point: MeteringPoint, options: FocusOptions) = withContext(Dispatchers.Main) {
        val meteringPoint = (point as? AndroidMeteringPoint)?.meteringPoint
            ?: throw VisionCameraException("The given `point` was not created by VisionCamera!")
        val meteringMode = (options.modes ?: getAllSupportedMeteringModes(meteringPoint)).toCameraXMeteringMode()
        val action = FocusMeteringAction.Builder(meteringPoint, meteringMode).apply {
            // Lock the 3A values after the operation completed, if desired.
            when (options.adaptiveness) {
                SceneAdaptiveness.LOCKED -> setLockingMode(meteringMode)
                SceneAdaptiveness.CONTINUOUS -> setLockingMode(0)
            }
            // Disable auto reset, or set it to a fixed duration (CameraX requires at least 1 ms).
            val autoResetAfter = options.autoResetAfter
            if (autoResetAfter == null) {
                disableAutoCancel()
            } else {
                setAutoCancelDuration(autoResetAfter.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS)
            }
        }.build()
        camera.cameraControl.startFocusAndMetering(action).await()
        Unit
    }

    /** Gets every [MeteringMode] the [camera] supports for the given [point]. */
    private fun getAllSupportedMeteringModes(point: androidx.camera.core.MeteringPoint): List<MeteringMode> =
        MeteringMode.entries.filter { mode ->
            val action = FocusMeteringAction.Builder(point, mode.toCameraXMeteringMode()).build()
            camera.cameraInfo.isFocusMeteringSupported(action)
        }

    override suspend fun resetFocus() = withContext(Dispatchers.Main) {
        camera.cameraControl.cancelFocusAndMetering().await()
        Unit
    }

    // CameraX does not support subject area changed listeners yet, see https://issuetracker.google.com/issues/505643406
    override fun addSubjectAreaChangedListener(onSubjectAreaChanged: () -> Unit): ListenerSubscription =
        ListenerSubscription { }

    // Manual Focus: CameraX only supports continuous AF, and no custom lens position.

    override val focusMode: FocusMode get() = FocusMode.CONTINUOUS_AUTO_FOCUS
    override val lensPosition: Double get() = 0.0

    override suspend fun setFocusLocked(lensPosition: Double) = throw VisionCameraException(FOCUS_LOCKING_UNSUPPORTED)

    override suspend fun lockCurrentFocus() = throw VisionCameraException(FOCUS_LOCKING_UNSUPPORTED)

    // Manual Exposure: CameraX only supports continuous AE, and no custom duration/ISO.

    override val exposureMode: ExposureMode get() = ExposureMode.CONTINUOUS_AUTO_EXPOSURE
    override val minExposureDuration: Double get() = 0.0
    override val maxExposureDuration: Double get() = 0.0
    override val minISO: Double get() = 0.0
    override val maxISO: Double get() = 0.0
    override val exposureDuration: Double get() = 0.0
    override val iso: Double get() = 0.0

    override suspend fun setExposureLocked(duration: Double, iso: Double) =
        throw VisionCameraException(EXPOSURE_LOCKING_UNSUPPORTED)

    override suspend fun lockCurrentExposure() = throw VisionCameraException(EXPOSURE_LOCKING_UNSUPPORTED)

    // Manual White Balance: CameraX only supports continuous AWB, and no custom gains.

    override val whiteBalanceMode: WhiteBalanceMode get() = WhiteBalanceMode.CONTINUOUS_AUTO_WHITE_BALANCE
    override val whiteBalanceGains: WhiteBalanceGains get() = WhiteBalanceGains(0.0, 0.0, 0.0)

    override fun convertWhiteBalanceTemperatureAndTintValues(
        whiteBalanceTemperatureAndTint: WhiteBalanceTemperatureAndTint,
    ): WhiteBalanceGains = throw VisionCameraException(WHITE_BALANCE_LOCKING_UNSUPPORTED)

    override suspend fun setWhiteBalanceLocked(whiteBalanceGains: WhiteBalanceGains) =
        throw VisionCameraException(WHITE_BALANCE_LOCKING_UNSUPPORTED)

    override suspend fun lockCurrentWhiteBalance() = throw VisionCameraException(WHITE_BALANCE_LOCKING_UNSUPPORTED)

    private companion object {
        const val FOCUS_LOCKING_UNSUPPORTED = "Setting Focus Lens Position is not yet supported on Android! " +
            "You can use AF metering locks (`focusTo(point, FocusOptions(modes = listOf(MeteringMode.AF), " +
            "adaptiveness = SceneAdaptiveness.LOCKED))`) instead."
        const val EXPOSURE_LOCKING_UNSUPPORTED =
            "Locking Exposure to manual duration/ISO values is not yet supported on Android! " +
                "You can use Exposure Bias (`setExposureBias(...)`) or AE metering locks (`focusTo(point, " +
                "FocusOptions(modes = listOf(MeteringMode.AE), adaptiveness = SceneAdaptiveness.LOCKED))`) instead."
        const val WHITE_BALANCE_LOCKING_UNSUPPORTED = "Locking White Balance Gains is not yet supported on Android! " +
            "You can use AWB metering locks (`focusTo(point, FocusOptions(modes = listOf(MeteringMode.AWB), " +
            "adaptiveness = SceneAdaptiveness.LOCKED))`) instead."

        /**
         * The duration of a zoom animation from [from] to [to], at [rate] doublings of the zoom factor per second
         * (the semantics of `AVCaptureDevice.ramp(toVideoZoomFactor:withRate:)`).
         */
        fun zoomAnimationDurationMillis(from: Double, to: Double, rate: Double): Long {
            val doublings = abs(ln(to / from) / ln(2.0))
            return (doublings / rate * MILLISECONDS_PER_SECOND).roundToLong()
        }
    }
}
