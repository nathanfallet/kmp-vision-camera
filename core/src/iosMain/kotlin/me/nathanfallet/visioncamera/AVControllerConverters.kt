package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureExposureMode
import platform.AVFoundation.AVCaptureExposureModeAutoExpose
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureExposureModeCustom
import platform.AVFoundation.AVCaptureExposureModeLocked
import platform.AVFoundation.AVCaptureFocusMode
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureFocusModeLocked
import platform.AVFoundation.AVCaptureWhiteBalanceMode
import platform.AVFoundation.AVCaptureWhiteBalanceModeAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeLocked

// Port of V5's AV+FocusMode.swift, AV+ExposureMode.swift and AV+WhiteBalanceMode.swift.

/** Converts an [AVCaptureFocusMode] to a [FocusMode]. */
internal fun focusModeOf(mode: AVCaptureFocusMode): FocusMode = when (mode) {
    AVCaptureFocusModeLocked -> FocusMode.LOCKED
    AVCaptureFocusModeAutoFocus -> FocusMode.AUTO_FOCUS
    AVCaptureFocusModeContinuousAutoFocus -> FocusMode.CONTINUOUS_AUTO_FOCUS
    else -> FocusMode.AUTO_FOCUS
}

/** Converts an [AVCaptureExposureMode] to an [ExposureMode]. */
internal fun exposureModeOf(mode: AVCaptureExposureMode): ExposureMode = when (mode) {
    AVCaptureExposureModeLocked -> ExposureMode.LOCKED
    AVCaptureExposureModeAutoExpose -> ExposureMode.AUTO_EXPOSURE
    AVCaptureExposureModeContinuousAutoExposure -> ExposureMode.CONTINUOUS_AUTO_EXPOSURE
    AVCaptureExposureModeCustom -> ExposureMode.CUSTOM
    else -> {
        Logger.error("Unknown ExposureMode: $mode")
        ExposureMode.CUSTOM
    }
}

/** Converts an [AVCaptureWhiteBalanceMode] to a [WhiteBalanceMode]. */
internal fun whiteBalanceModeOf(mode: AVCaptureWhiteBalanceMode): WhiteBalanceMode = when (mode) {
    AVCaptureWhiteBalanceModeLocked -> WhiteBalanceMode.LOCKED
    AVCaptureWhiteBalanceModeAutoWhiteBalance -> WhiteBalanceMode.AUTO_WHITE_BALANCE
    AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance -> WhiteBalanceMode.CONTINUOUS_AUTO_WHITE_BALANCE
    else -> WhiteBalanceMode.AUTO_WHITE_BALANCE
}
