package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.automaticallyEnablesLowLightBoostWhenAvailable
import platform.AVFoundation.displayVideoZoomFactorMultiplier
import platform.AVFoundation.exposureTargetBias
import platform.AVFoundation.geometricDistortionCorrectionEnabled
import platform.AVFoundation.geometricDistortionCorrectionSupported
import platform.AVFoundation.hasTorch
import platform.AVFoundation.lowLightBoostSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.setExposureTargetBias
import platform.AVFoundation.smoothAutoFocusEnabled
import platform.AVFoundation.smoothAutoFocusSupported
import platform.AVFoundation.torchLevel
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.darwin.dispatch_queue_t

/**
 * The iOS [CameraController], controlling an [AVCaptureDevice] connected to an `AVCaptureSession` (port of V5's
 * `HybridCameraController.swift`).
 *
 * All device changes run under a device lock on the session's serial [queue].
 */
internal class IosCameraController(
    override val device: IosCameraDevice,
    private val queue: dispatch_queue_t,
) : CameraController {
    private val captureDevice: AVCaptureDevice = device.device

    // Device State

    override val isConnected: Boolean get() = captureDevice.connected
    override val isSuspended: Boolean get() = captureDevice.suspended
    override val isUsedByAnotherApp: Boolean get() = false
    override val isLowLightBoostEnabled: Boolean get() = captureDevice.automaticallyEnablesLowLightBoostWhenAvailable
    override val isSmoothAutoFocusEnabled: Boolean get() = captureDevice.smoothAutoFocusEnabled
    override val isDistortionCorrectionEnabled: Boolean get() = captureDevice.geometricDistortionCorrectionEnabled

    override suspend fun configure(config: CameraControllerConfiguration) = captureDevice.withLock(queue) {
        val device = captureDevice
        config.enableLowLightBoost?.let { enableLowLightBoost ->
            if (!device.lowLightBoostSupported) {
                throw VisionCameraException("This CameraDevice does not support `enableLowLightBoost`!")
            }
            device.automaticallyEnablesLowLightBoostWhenAvailable = enableLowLightBoost
        }
        config.enableSmoothAutoFocus?.let { enableSmoothAutoFocus ->
            if (!device.smoothAutoFocusSupported) {
                throw VisionCameraException("This CameraDevice does not support `enableSmoothAutoFocus`!")
            }
            device.smoothAutoFocusEnabled = enableSmoothAutoFocus
        }
        config.enableDistortionCorrection?.let { enableDistortionCorrection ->
            if (!device.geometricDistortionCorrectionSupported) {
                throw VisionCameraException("This CameraDevice does not support `enableDistortionCorrection`!")
            }
            device.geometricDistortionCorrectionEnabled = enableDistortionCorrection
        }
    }

    // Zoom

    override val zoom: Double get() = captureDevice.videoZoomFactor
    override val minZoom: Double get() = captureDevice.minAvailableVideoZoomFactor
    override val maxZoom: Double get() = captureDevice.maxAvailableVideoZoomFactor

    override val displayableZoomFactor: Double
        get() = if (isIOSAtLeast(major = 18)) zoom * captureDevice.displayVideoZoomFactorMultiplier else zoom

    override suspend fun setZoom(zoom: Double) = captureDevice.withLock(queue) {
        val device = captureDevice
        // 1. Clamp zoom within the device's valid range
        val clamped = zoom.coerceIn(device.minAvailableVideoZoomFactor, device.maxAvailableVideoZoomFactor)
        // 2. Clamp zoom to the active format's max zoom (might be slightly smaller than device max zoom)
        val clampedForFormat = minOf(clamped, device.activeFormat.videoMaxZoomFactor)
        // 3. Apply zoom
        device.videoZoomFactor = clampedForFormat
    }

    // Flash

    override val torchStrength: Double get() = captureDevice.torchLevel.toDouble()
    override val torchMode: TorchMode get() = torchModeOf(captureDevice.torchMode)

    override suspend fun setTorchMode(mode: TorchMode) = captureDevice.withLock(queue) {
        // 1. Ensure we have a torch
        if (!captureDevice.hasTorch) {
            // We set it to off, we can ignore that we don't have a torch.
            if (mode == TorchMode.OFF) return@withLock
            throw VisionCameraException("Tried setting torch mode to `$mode`, but the device does not have a `torch`!")
        }
        // 2. Set mode to on or off
        captureDevice.torchMode = when (mode) {
            TorchMode.OFF -> AVCaptureTorchModeOff
            TorchMode.ON -> AVCaptureTorchModeOn
        }
    }

    // Exposure

    override val exposureBias: Double get() = captureDevice.exposureTargetBias.toDouble()

    override suspend fun setExposureBias(exposure: Double) = captureDevice.withLock(queue) { resolve, _ ->
        val device = captureDevice
        // 1. Clamp exposure bias within the device's valid range
        val clamped = exposure.toFloat().coerceIn(device.minExposureTargetBias, device.maxExposureTargetBias)
        // 2. Set Exposure Bias, and resolve once it has been applied
        device.setExposureTargetBias(clamped) { _ -> resolve() }
    }

    override fun toString(): String = "CameraController(device: $device)"

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
