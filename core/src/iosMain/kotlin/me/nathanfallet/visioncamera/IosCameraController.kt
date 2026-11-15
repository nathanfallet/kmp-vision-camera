package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.cValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureExposureDurationCurrent
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureISOCurrent
import platform.AVFoundation.AVCaptureLensPositionCurrent
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureWhiteBalanceGains
import platform.AVFoundation.AVCaptureWhiteBalanceGainsCurrent
import platform.AVFoundation.AVCaptureWhiteBalanceModeAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceTemperatureAndTintValues
import platform.AVFoundation.ISO
import platform.AVFoundation.automaticallyEnablesLowLightBoostWhenAvailable
import platform.AVFoundation.cancelVideoZoomRamp
import platform.AVFoundation.deviceWhiteBalanceGains
import platform.AVFoundation.deviceWhiteBalanceGainsForTemperatureAndTintValues
import platform.AVFoundation.displayVideoZoomFactorMultiplier
import platform.AVFoundation.exposureDuration
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.exposurePointOfInterestSupported
import platform.AVFoundation.exposureTargetBias
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
import platform.AVFoundation.geometricDistortionCorrectionEnabled
import platform.AVFoundation.geometricDistortionCorrectionSupported
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isWhiteBalanceModeSupported
import platform.AVFoundation.lensPosition
import platform.AVFoundation.lowLightBoostSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.maxWhiteBalanceGain
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.rampToVideoZoomFactor
import platform.AVFoundation.rampingVideoZoom
import platform.AVFoundation.setExposureModeCustomWithDuration
import platform.AVFoundation.setExposureTargetBias
import platform.AVFoundation.setFocusModeLockedWithLensPosition
import platform.AVFoundation.setTorchModeOnWithLevel
import platform.AVFoundation.setWhiteBalanceModeLockedWithDeviceWhiteBalanceGains
import platform.AVFoundation.smoothAutoFocusEnabled
import platform.AVFoundation.smoothAutoFocusSupported
import platform.AVFoundation.torchLevel
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.AVFoundation.whiteBalanceMode
import platform.CoreGraphics.CGPointMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.darwin.dispatch_queue_t
import kotlin.time.Duration.Companion.milliseconds

/**
 * The iOS [CameraController], controlling an [AVCaptureDevice] connected to an `AVCaptureSession` (port of V5's
 * `HybridCameraController.swift`).
 *
 * All device changes run under a device lock on the session's serial [queue].
 */
@Suppress("TooManyFunctions")
internal class IosCameraController(
    override val device: IosCameraDevice,
    internal val queue: dispatch_queue_t,
) : CameraController {
    internal val captureDevice: AVCaptureDevice = device.device

    /** The currently running [focusTo] operation, only accessed on the [queue]. */
    private var activeMeteringTask: MeteringTask? = null

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

    override suspend fun startZoomAnimation(zoom: Double, rate: Double) = captureDevice.withLock(queue) { resolve, _ ->
        if (captureDevice.videoZoomFactor == zoom) {
            // We are already exactly on our target value
            resolve()
            return@withLock
        }
        captureDevice.rampToVideoZoomFactor(zoom, withRate = rate.toFloat())
        // V5 observes `isRampingVideoZoom` with KVO, which Kotlin/Native cannot do: we poll it instead.
        resolveWhenZoomRampFinished(resolve)
    }

    private fun resolveWhenZoomRampFinished(resolve: () -> Unit) {
        queue.asyncAfter(ZOOM_RAMP_POLL_INTERVAL) {
            if (captureDevice.rampingVideoZoom) resolveWhenZoomRampFinished(resolve) else resolve()
        }
    }

    override suspend fun cancelZoomAnimation() = captureDevice.withLock(queue) {
        captureDevice.cancelVideoZoomRamp()
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

    override suspend fun enableTorchWithStrength(strength: Double) = captureDevice.withLock(queue) {
        if (!device.supportsTorchStrength) {
            throw VisionCameraException(
                "Cannot enable torch - this CameraDevice does not support setting torch strength! " +
                    "(See `device.supportsTorchStrength`)",
            )
        }
        val minStrength = captureDevice.minTorchStrength
        val maxStrength = captureDevice.maxTorchStrength
        if (strength !in minStrength..maxStrength) {
            throw VisionCameraException(
                "`strength` is not within the device's `minTorchStrength` and `maxTorchStrength` range! " +
                    "(Received: $strength, `device.minTorchStrength`: $minStrength, " +
                    "`device.maxTorchStrength`: $maxStrength)",
            )
        }
        throwingNSError("Failed to enable the torch") { error ->
            captureDevice.setTorchModeOnWithLevel(strength.toFloat(), error)
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

    // Focus

    override suspend fun focusTo(point: MeteringPoint, options: FocusOptions) =
        captureDevice.withLock(queue) { resolve, reject ->
            val modes = options.modes ?: allSupportedMeteringModes()
            if (modes.isEmpty()) throw VisionCameraException("MeteringModes cannot be empty!")

            // Cancel any previous tasks
            activeMeteringTask?.cancel()
            activeMeteringTask = null

            // Create a new MeteringTask and start the desired metering operations
            val task = MeteringTask(
                device = captureDevice,
                adaptiveness = options.adaptiveness,
                autoResetAfter = options.autoResetAfter,
                queue = queue,
            )
            val normalizedPoint = CGPointMake(point.normalizedX, point.normalizedY)
            if (MeteringMode.AE in modes) task.startMeteringAE(normalizedPoint, options.responsiveness)
            if (MeteringMode.AF in modes) task.startMeteringAF(normalizedPoint, options.responsiveness)
            if (MeteringMode.AWB in modes) task.startMeteringAWB(options.responsiveness)

            // Start listening to updates
            task.startListening(onComplete = resolve, onError = reject)
            activeMeteringTask = task
        }

    /** Gets a list of all [MeteringMode]s that are supported on this device. */
    private fun allSupportedMeteringModes(): List<MeteringMode> = buildList {
        if (captureDevice.exposurePointOfInterestSupported) add(MeteringMode.AE)
        if (captureDevice.focusPointOfInterestSupported) add(MeteringMode.AF)
        // White Balance adjusting is not point-based, but it still requires a supported auto mode.
        if (captureDevice.isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeAutoWhiteBalance) ||
            captureDevice.isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance)
        ) {
            add(MeteringMode.AWB)
        }
    }

    override suspend fun resetFocus() = captureDevice.withLock(queue) {
        // Cancel an ongoing focus operation
        activeMeteringTask?.cancel()
        activeMeteringTask = null

        Logger.info("Resetting focus...")
        val device = captureDevice
        val center = CGPointMake(CENTER, CENTER)
        // Reset AF to center + continuous tracking
        if (device.focusPointOfInterestSupported) device.focusPointOfInterest = center
        if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
            device.focusMode = AVCaptureFocusModeContinuousAutoFocus
        }
        // Reset AE to center + continuous tracking
        if (device.exposurePointOfInterestSupported) device.exposurePointOfInterest = center
        if (device.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
            device.exposureMode = AVCaptureExposureModeContinuousAutoExposure
        }
        // Reset AWB to continuous tracking
        if (device.isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance)) {
            device.whiteBalanceMode = AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
        }
    }

    override fun addSubjectAreaChangedListener(onSubjectAreaChanged: () -> Unit): ListenerSubscription =
        SubjectAreaMonitor.addObserver(captureDevice, onSubjectAreaChanged)

    // Manual Focus

    override val focusMode: FocusMode get() = focusModeOf(captureDevice.focusMode)
    override val lensPosition: Double get() = captureDevice.lensPosition.toDouble()

    override suspend fun setFocusLocked(lensPosition: Double) = captureDevice.withLock(queue) { resolve, _ ->
        // 1. Ensure values are within valid range
        if (lensPosition !in 0.0..1.0) {
            throw VisionCameraException("`lensPosition` is not within 0.0 to 1.0 range! (Received: $lensPosition)")
        }
        // 2. Lock Focus, and resolve once it locked
        captureDevice.setFocusModeLockedWithLensPosition(lensPosition.toFloat()) { _ -> resolve() }
    }

    override suspend fun lockCurrentFocus() = captureDevice.withLock(queue) { resolve, _ ->
        captureDevice.setFocusModeLockedWithLensPosition(AVCaptureLensPositionCurrent) { _ -> resolve() }
    }

    // Manual Exposure

    override val exposureMode: ExposureMode get() = exposureModeOf(captureDevice.exposureMode)
    override val minExposureDuration: Double get() = CMTimeGetSeconds(captureDevice.activeFormat.minExposureDuration)
    override val maxExposureDuration: Double get() = CMTimeGetSeconds(captureDevice.activeFormat.maxExposureDuration)
    override val minISO: Double get() = captureDevice.activeFormat.minISO.toDouble()
    override val maxISO: Double get() = captureDevice.activeFormat.maxISO.toDouble()
    override val exposureDuration: Double get() = CMTimeGetSeconds(captureDevice.exposureDuration)
    override val iso: Double get() = captureDevice.ISO.toDouble()

    override suspend fun setExposureLocked(duration: Double, iso: Double) =
        captureDevice.withLock(queue) { resolve, _ ->
            val format = captureDevice.activeFormat
            // 1. Ensure values are within valid range
            val minDuration = CMTimeGetSeconds(format.minExposureDuration)
            val maxDuration = CMTimeGetSeconds(format.maxExposureDuration)
            if (duration !in minDuration..maxDuration) {
                throw VisionCameraException(
                    "`duration` is not within the currently selected Format's `minExposureDuration` and " +
                        "`maxExposureDuration` range! (Received: $duration, `activeFormat.minExposureDuration`: " +
                        "$minDuration, `activeFormat.maxExposureDuration`: $maxDuration)",
                )
            }
            val isoValue = iso.toFloat()
            if (isoValue !in format.minISO..format.maxISO) {
                throw VisionCameraException(
                    "`iso` is not within the currently selected Format's `minISO` and `maxISO` range! " +
                        "(Received: $isoValue, `activeFormat.minISO`: ${format.minISO}, " +
                        "`activeFormat.maxISO`: ${format.maxISO})",
                )
            }
            // 2. Set the Exposure Duration/ISO, and resolve once it has been applied
            val time = CMTimeMakeWithSeconds(duration, preferredTimescale = EXPOSURE_TIMESCALE)
            captureDevice.setExposureModeCustomWithDuration(time, ISO = isoValue) { _ -> resolve() }
        }

    override suspend fun lockCurrentExposure() = captureDevice.withLock(queue) { resolve, _ ->
        captureDevice.setExposureModeCustomWithDuration(
            AVCaptureExposureDurationCurrent.readValue(),
            ISO = AVCaptureISOCurrent,
        ) { _ -> resolve() }
    }

    // Manual White Balance

    override val whiteBalanceMode: WhiteBalanceMode get() = whiteBalanceModeOf(captureDevice.whiteBalanceMode)

    override val whiteBalanceGains: WhiteBalanceGains
        get() = captureDevice.deviceWhiteBalanceGains.toWhiteBalanceGains()

    override fun convertWhiteBalanceTemperatureAndTintValues(
        whiteBalanceTemperatureAndTint: WhiteBalanceTemperatureAndTint,
    ): WhiteBalanceGains {
        // 1. Convert our type to the AVFoundation type
        val tintValues = cValue<AVCaptureWhiteBalanceTemperatureAndTintValues> {
            temperature = whiteBalanceTemperatureAndTint.temperature.toFloat()
            tint = whiteBalanceTemperatureAndTint.tint.toFloat()
        }
        // 2. Do the actual device conversion, and convert the returned AVFoundation gains to our type
        return captureDevice.deviceWhiteBalanceGainsForTemperatureAndTintValues(tintValues).toWhiteBalanceGains()
    }

    override suspend fun setWhiteBalanceLocked(whiteBalanceGains: WhiteBalanceGains) =
        captureDevice.withLock(queue) { resolve, _ ->
            // 1. Ensure values are within valid range
            val maxGain = captureDevice.maxWhiteBalanceGain
            val redGain = whiteBalanceGains.redGain.toFloat().requireGainInRange("redGain", maxGain)
            val greenGain = whiteBalanceGains.greenGain.toFloat().requireGainInRange("greenGain", maxGain)
            val blueGain = whiteBalanceGains.blueGain.toFloat().requireGainInRange("blueGain", maxGain)
            // 2. Lock White Balance Gains, and resolve once it is locked
            val values = cValue<AVCaptureWhiteBalanceGains> {
                this.redGain = redGain
                this.greenGain = greenGain
                this.blueGain = blueGain
            }
            captureDevice.setWhiteBalanceModeLockedWithDeviceWhiteBalanceGains(values) { _ -> resolve() }
        }

    override suspend fun lockCurrentWhiteBalance() = captureDevice.withLock(queue) { resolve, _ ->
        captureDevice.setWhiteBalanceModeLockedWithDeviceWhiteBalanceGains(
            AVCaptureWhiteBalanceGainsCurrent.readValue(),
        ) { _ -> resolve() }
    }

    override fun toString(): String = "CameraController(device: $device)"

    private companion object {
        const val CENTER = 0.5
        const val EXPOSURE_TIMESCALE = 1_000_000
        val ZOOM_RAMP_POLL_INTERVAL = 16.milliseconds

        fun CValue<AVCaptureWhiteBalanceGains>.toWhiteBalanceGains(): WhiteBalanceGains = useContents {
            WhiteBalanceGains(
                redGain = redGain.toDouble(),
                blueGain = blueGain.toDouble(),
                greenGain = greenGain.toDouble(),
            )
        }

        /** Ensures this gain is within `1` and [maxGain], or throws. */
        fun Float.requireGainInRange(name: String, maxGain: Float): Float {
            if (this !in 1f..maxGain) {
                throw VisionCameraException(
                    "`whiteBalanceGains.$name` is not within the range of `1` to `device.maxWhiteBalanceGain`! " +
                        "(Received: $this, min: 1.0, `device.maxWhiteBalanceGain`: $maxGain)",
                )
            }
            return this
        }
    }
}
