package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureExposureMode
import platform.AVFoundation.AVCaptureExposureModeAutoExpose
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureExposureModeLocked
import platform.AVFoundation.AVCaptureFocusMode
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureFocusModeLocked
import platform.AVFoundation.AVCaptureWhiteBalanceMode
import platform.AVFoundation.AVCaptureWhiteBalanceModeAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeLocked
import platform.AVFoundation.adjustingExposure
import platform.AVFoundation.adjustingFocus
import platform.AVFoundation.adjustingWhiteBalance
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.exposurePointOfInterestSupported
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isWhiteBalanceModeSupported
import platform.AVFoundation.whiteBalanceMode
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.darwin.dispatch_queue_t
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Represents an ongoing Metering operation in the Camera pipeline (port of V5's `MeteringTask.swift`).
 *
 * A Metering operation can track one or more metering modes (AE, AF, AWB), and will call its `onComplete` listener
 * once all desired metering modes have successfully settled.
 *
 * To avoid premature completion, all metering tasks have to be settled for a duration of at least 120ms before the
 * completion handler is called.
 *
 * If a [MeteringTask] is canceled or times out, the `onError` listener will be called.
 *
 * V5 tracks `isAdjustingExposure`/`isAdjustingFocus`/`isAdjustingWhiteBalance` with KVO and polls the settled
 * states. Kotlin/Native cannot override `observeValueForKeyPath`, so we read those states in the same poll instead.
 *
 * All methods must be called on the given [queue].
 */
@Suppress("TooManyFunctions")
internal class MeteringTask(
    private val device: AVCaptureDevice,
    private val adaptiveness: SceneAdaptiveness,
    private val autoResetAfter: Duration?,
    private val queue: dispatch_queue_t,
) {
    /** If a [MeteringMode] is not in here, we don't track it. */
    private val meteringStates = mutableMapOf<MeteringMode, MeteringProgress>()
    private var isFinished = false
    private var onComplete: (() -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    private data class MeteringProgress(val settledAt: TimeMark? = null)

    /**
     * Starts tracking progress for this [MeteringTask].
     */
    fun startListening(onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        this.onComplete = onComplete
        this.onError = onError
        // The timer periodically polls AE/AF/AWB state - this is how we can ensure the states have been stable for
        // 120ms+ and aren't just fluctuating.
        schedulePoll(Duration.ZERO)
        queue.asyncAfter(TIMEOUT) { onTimedOut() }
        Logger.info("Started metering operations...")
    }

    /**
     * Cancels this [MeteringTask].
     */
    fun cancel() {
        if (isFinished) return
        Logger.info("Canceling metering operations...")
        onError?.invoke(VisionCameraException("The metering operation has been canceled!"))
        isFinished = true
        destroy()
    }

    private fun destroy() {
        Logger.info("Destroying `MeteringTask`...")
        if (!isFinished) {
            onError?.invoke(VisionCameraException("The metering operation has timeouted!"))
            isFinished = true
        }
    }

    private fun onTimedOut() {
        if (isFinished) return
        Logger.info("Metering operations timed out after $TIMEOUT!")
        destroy()
    }

    private fun schedulePoll(delay: Duration) {
        queue.asyncAfter(delay) {
            if (isFinished) return@asyncAfter
            poll()
            if (!isFinished) schedulePoll(POLL_INTERVAL)
        }
    }

    private fun poll() {
        for (mode in meteringStates.keys.toList()) {
            onMeteringChanged(mode, isAdjusting = isAdjusting(mode))
        }
        update()
    }

    private fun isAdjusting(mode: MeteringMode): Boolean = when (mode) {
        MeteringMode.AE -> device.adjustingExposure
        MeteringMode.AF -> device.adjustingFocus
        MeteringMode.AWB -> device.adjustingWhiteBalance
    }

    private fun update() {
        if (isFinished) return

        // 1. Check if all metering states have been settled for at least the `MIN_STABLE_DURATION` (120ms).
        val allSettledForStableDuration = meteringStates.values.all { progress ->
            // If `settledAt` is `null`, this metering state has not been settled at all yet!
            val settledAt = progress.settledAt ?: return@all false
            settledAt.elapsedNow() >= MIN_STABLE_DURATION
        }
        if (!allSettledForStableDuration) return

        // 2. All states have been settled since 120ms+ now - complete it!
        Logger.info(
            "All metering operations have been settled for at least $MIN_STABLE_DURATION - completed!",
        )
        onComplete?.invoke()
        isFinished = true
        destroy()
        // After completion, update AE/AF/AWB to locked or continuous tracking
        runCatching {
            when (adaptiveness) {
                SceneAdaptiveness.CONTINUOUS -> setMeteringValuesToContinuous()
                SceneAdaptiveness.LOCKED -> setMeteringValuesToLocked()
            }
        }
        // Start the timer for auto reset (if enabled)
        autoResetAfter?.let { delay ->
            queue.asyncAfter(delay) { runCatching { resetMeteringValues() } }
        }
    }

    private fun setMeteringValuesToLocked() {
        Logger.info("Locking AE/AF/AWB values...")
        setMeteringValues(
            focusPoint = null,
            exposure = AVCaptureExposureModeLocked,
            focus = AVCaptureFocusModeLocked,
            whiteBalance = AVCaptureWhiteBalanceModeLocked,
        )
    }

    private fun setMeteringValuesToContinuous() {
        Logger.info("Setting AE/AF/AWB values to continuously auto-focus...")
        setMeteringValues(
            focusPoint = null,
            exposure = AVCaptureExposureModeContinuousAutoExposure,
            focus = AVCaptureFocusModeContinuousAutoFocus,
            whiteBalance = AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance,
        )
    }

    private fun resetMeteringValues() {
        Logger.info("Resetting AE/AF/AWB values to continuously auto-focus at center...")
        setMeteringValues(
            focusPoint = CGPointMake(CENTER, CENTER),
            exposure = AVCaptureExposureModeContinuousAutoExposure,
            focus = AVCaptureFocusModeContinuousAutoFocus,
            whiteBalance = AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance,
        )
    }

    private fun setMeteringValues(
        focusPoint: CValue<CGPoint>?,
        exposure: AVCaptureExposureMode,
        focus: AVCaptureFocusMode,
        whiteBalance: AVCaptureWhiteBalanceMode,
    ) {
        device.lockForConfigurationOrThrow()
        try {
            // Lock AE
            if (MeteringMode.AE in meteringStates) {
                if (focusPoint != null && device.exposurePointOfInterestSupported) {
                    device.exposurePointOfInterest = focusPoint
                }
                if (device.isExposureModeSupported(exposure)) device.exposureMode = exposure
            }
            // Lock AF
            if (MeteringMode.AF in meteringStates) {
                if (focusPoint != null && device.focusPointOfInterestSupported) {
                    device.focusPointOfInterest = focusPoint
                }
                if (device.isFocusModeSupported(focus)) device.focusMode = focus
            }
            // Lock AWB
            if (MeteringMode.AWB in meteringStates && device.isWhiteBalanceModeSupported(whiteBalance)) {
                device.whiteBalanceMode = whiteBalance
            }
        } finally {
            device.unlockForConfiguration()
        }
    }

    private fun onMeteringRequested(mode: MeteringMode) {
        meteringStates[mode] = MeteringProgress()
    }

    private fun onMeteringChanged(mode: MeteringMode, isAdjusting: Boolean) {
        if (isFinished) return
        meteringStates[mode] = if (isAdjusting) {
            MeteringProgress()
        } else {
            MeteringProgress(settledAt = meteringStates[mode]?.settledAt ?: TimeSource.Monotonic.markNow())
        }
    }

    /**
     * Starts metering exposure (AE) to the given [point]. The [device] must be locked for configuration.
     */
    fun startMeteringAE(point: CValue<CGPoint>, responsiveness: FocusResponsiveness) {
        if (!device.exposurePointOfInterestSupported) {
            throw VisionCameraException("Metering Mode `AE` is not supported on this device!")
        }
        Logger.info("Started metering AE...")
        // Track AE completion
        onMeteringRequested(MeteringMode.AE)
        // Request AE to lock to the specific point
        device.exposurePointOfInterest = point
        device.exposureMode = exposureModeFor(responsiveness)
    }

    /**
     * Starts metering focus (AF) to the given [point]. The [device] must be locked for configuration.
     */
    fun startMeteringAF(point: CValue<CGPoint>, responsiveness: FocusResponsiveness) {
        if (!device.focusPointOfInterestSupported) {
            throw VisionCameraException("Metering Mode `AF` is not supported on this device!")
        }
        Logger.info("Started metering AF...")
        // Track AF completion
        onMeteringRequested(MeteringMode.AF)
        // Request AF to lock to the specific point
        device.focusPointOfInterest = point
        device.focusMode = focusModeFor(responsiveness)
    }

    /**
     * Starts metering white balance (AWB). The [device] must be locked for configuration.
     */
    fun startMeteringAWB(responsiveness: FocusResponsiveness) {
        Logger.info("Started metering AWB...")
        // Track AWB completion
        onMeteringRequested(MeteringMode.AWB)
        // Request AWB to focus
        device.whiteBalanceMode = whiteBalanceModeFor(responsiveness)
    }

    private fun exposureModeFor(responsiveness: FocusResponsiveness): AVCaptureExposureMode = preferredMode(
        responsiveness = responsiveness,
        snappy = AVCaptureExposureModeAutoExpose,
        steady = AVCaptureExposureModeContinuousAutoExposure,
        isSupported = { device.isExposureModeSupported(it) },
    ) ?: throw VisionCameraException("Metering Mode `AE` is not supported on this device!")

    private fun focusModeFor(responsiveness: FocusResponsiveness): AVCaptureFocusMode = preferredMode(
        responsiveness = responsiveness,
        snappy = AVCaptureFocusModeAutoFocus,
        steady = AVCaptureFocusModeContinuousAutoFocus,
        isSupported = { device.isFocusModeSupported(it) },
    ) ?: throw VisionCameraException("Metering Mode `AF` is not supported on this device!")

    private fun whiteBalanceModeFor(responsiveness: FocusResponsiveness): AVCaptureWhiteBalanceMode = preferredMode(
        responsiveness = responsiveness,
        snappy = AVCaptureWhiteBalanceModeAutoWhiteBalance,
        steady = AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance,
        isSupported = { device.isWhiteBalanceModeSupported(it) },
    ) ?: throw VisionCameraException("Metering Mode `AWB` is not supported on this device!")

    private companion object {
        val MIN_STABLE_DURATION = 120.milliseconds
        val POLL_INTERVAL = 50.milliseconds
        val TIMEOUT = 10.seconds
        const val CENTER = 0.5

        /**
         * Uses the [snappy] (auto, one-shot) mode for [FocusResponsiveness.SNAPPY] and the [steady] (continuous) mode
         * for [FocusResponsiveness.STEADY] if supported, and falls back to the other one otherwise.
         */
        fun preferredMode(
            responsiveness: FocusResponsiveness,
            snappy: Long,
            steady: Long,
            isSupported: (Long) -> Boolean,
        ): Long? {
            val preferred = when (responsiveness) {
                FocusResponsiveness.SNAPPY -> listOf(snappy, steady)
                FocusResponsiveness.STEADY -> listOf(steady, snappy)
            }
            return preferred.firstOrNull(isSupported)
        }
    }
}
