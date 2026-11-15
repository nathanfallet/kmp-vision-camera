package me.nathanfallet.visioncamera

import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.LowLightBoostState
import androidx.camera.core.TorchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * The Android [CameraController], controlling a bound CameraX [Camera].
 */
internal class AndroidCameraController(
    /** The bound CameraX [Camera]. */
    val camera: Camera,
) : CameraController {
    override val device: CameraDevice = AndroidCameraDevice(camera.cameraInfo)
    private val cameraState: CameraState? get() = camera.cameraInfo.cameraState.value

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

    override val zoom: Double get() = camera.cameraInfo.zoomState.value?.zoomRatio?.toDouble() ?: 1.0
    override val minZoom: Double get() = camera.cameraInfo.zoomState.value?.minZoomRatio?.toDouble() ?: 1.0
    override val maxZoom: Double get() = camera.cameraInfo.zoomState.value?.maxZoomRatio?.toDouble() ?: 1.0
    override val displayableZoomFactor: Double get() = zoom * camera.cameraInfo.intrinsicZoomRatio

    override suspend fun setZoom(zoom: Double) = withContext(Dispatchers.Main) {
        camera.cameraControl.setZoomRatio(zoom.coerceIn(minZoom, maxZoom).toFloat()).await()
        Unit
    }

    override val torchStrength: Double get() = camera.cameraInfo.torchStrengthLevel.value?.toDouble() ?: 0.0
    override val torchMode: TorchMode
        get() = torchModeFromTorchState(camera.cameraInfo.torchState.value ?: TorchState.OFF)

    override suspend fun setTorchMode(mode: TorchMode) = withContext(Dispatchers.Main) {
        camera.cameraControl.enableTorch(mode == TorchMode.ON).await()
        Unit
    }

    override val exposureBias: Double get() = camera.cameraInfo.exposureState.exposureCompensationIndex.toDouble()

    override suspend fun setExposureBias(exposure: Double) = withContext(Dispatchers.Main) {
        camera.cameraControl.setExposureCompensationIndex(exposure.toInt()).await()
        Unit
    }

    // Phase 1 features below are not implemented on this platform yet.

    override suspend fun focusTo(point: MeteringPoint, options: FocusOptions) = notImplementedYet("focusTo")

    override suspend fun resetFocus() = notImplementedYet("resetFocus")

    override fun addSubjectAreaChangedListener(onSubjectAreaChanged: () -> Unit): ListenerSubscription =
        ListenerSubscription { }

    override suspend fun startZoomAnimation(zoom: Double, rate: Double) = notImplementedYet("startZoomAnimation")

    override suspend fun cancelZoomAnimation() = notImplementedYet("cancelZoomAnimation")

    override suspend fun enableTorchWithStrength(strength: Double) = notImplementedYet("enableTorchWithStrength")

    override val focusMode: FocusMode get() = FocusMode.CONTINUOUS_AUTO_FOCUS
    override val lensPosition: Double get() = 0.0

    override suspend fun setFocusLocked(lensPosition: Double) = notImplementedYet("setFocusLocked")

    override suspend fun lockCurrentFocus() = notImplementedYet("lockCurrentFocus")

    override val exposureMode: ExposureMode get() = ExposureMode.CONTINUOUS_AUTO_EXPOSURE
    override val minExposureDuration: Double get() = 0.0
    override val maxExposureDuration: Double get() = 0.0
    override val minISO: Double get() = 0.0
    override val maxISO: Double get() = 0.0
    override val exposureDuration: Double get() = 0.0
    override val iso: Double get() = 0.0

    override suspend fun setExposureLocked(duration: Double, iso: Double) = notImplementedYet("setExposureLocked")

    override suspend fun lockCurrentExposure() = notImplementedYet("lockCurrentExposure")

    override val whiteBalanceMode: WhiteBalanceMode get() = WhiteBalanceMode.CONTINUOUS_AUTO_WHITE_BALANCE
    override val whiteBalanceGains: WhiteBalanceGains get() = WhiteBalanceGains(0.0, 0.0, 0.0)

    override fun convertWhiteBalanceTemperatureAndTintValues(
        whiteBalanceTemperatureAndTint: WhiteBalanceTemperatureAndTint,
    ): WhiteBalanceGains = notImplementedYet("convertWhiteBalanceTemperatureAndTintValues")

    override suspend fun setWhiteBalanceLocked(whiteBalanceGains: WhiteBalanceGains) =
        notImplementedYet("setWhiteBalanceLocked")

    override suspend fun lockCurrentWhiteBalance() = notImplementedYet("lockCurrentWhiteBalance")
}
