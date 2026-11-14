package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureSessionInterruptionReason
import platform.AVFoundation.AVCaptureSessionInterruptionReasonAudioDeviceInUseByAnotherClient
import platform.AVFoundation.AVCaptureSessionInterruptionReasonSensitiveContentMitigationActivated
import platform.AVFoundation.AVCaptureSessionInterruptionReasonVideoDeviceInUseByAnotherClient
import platform.AVFoundation.AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableDueToSystemPressure
import platform.AVFoundation.AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableInBackground
import platform.AVFoundation.AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableWithMultipleForegroundApps
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown

// Port of V5's AV+InterruptionReason.swift, AV+CameraOrientation.swift, UI+CameraOrientation.swift and
// AV+PreviewResizeMode.swift.

/** Converts an [AVCaptureSessionInterruptionReason] to an [InterruptionReason]. */
internal fun interruptionReasonOf(reason: AVCaptureSessionInterruptionReason): InterruptionReason = when (reason) {
    AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableInBackground ->
        InterruptionReason.VIDEO_DEVICE_NOT_AVAILABLE_IN_BACKGROUND
    AVCaptureSessionInterruptionReasonAudioDeviceInUseByAnotherClient ->
        InterruptionReason.AUDIO_DEVICE_IN_USE_BY_ANOTHER_CLIENT
    AVCaptureSessionInterruptionReasonVideoDeviceInUseByAnotherClient ->
        InterruptionReason.VIDEO_DEVICE_IN_USE_BY_ANOTHER_CLIENT
    AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableWithMultipleForegroundApps ->
        InterruptionReason.VIDEO_DEVICE_NOT_AVAILABLE_WITH_MULTIPLE_FOREGROUND_APPS
    AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableDueToSystemPressure ->
        InterruptionReason.VIDEO_DEVICE_NOT_AVAILABLE_DUE_TO_SYSTEM_PRESSURE
    AVCaptureSessionInterruptionReasonSensitiveContentMitigationActivated ->
        InterruptionReason.SENSITIVE_CONTENT_MITIGATION_ACTIVATED
    else -> InterruptionReason.UNKNOWN
}

/** Converts an [AVCaptureVideoOrientation] to a [CameraOrientation]. */
internal fun cameraOrientationOf(orientation: AVCaptureVideoOrientation): CameraOrientation = when (orientation) {
    AVCaptureVideoOrientationPortrait -> CameraOrientation.UP
    AVCaptureVideoOrientationPortraitUpsideDown -> CameraOrientation.DOWN
    AVCaptureVideoOrientationLandscapeRight -> CameraOrientation.LEFT
    AVCaptureVideoOrientationLandscapeLeft -> CameraOrientation.RIGHT
    else -> error("AVCaptureVideoOrientation has unknown value: $orientation")
}

/** Converts a [CameraOrientation] to an [AVCaptureVideoOrientation]. */
internal fun CameraOrientation.toAVCaptureVideoOrientation(): AVCaptureVideoOrientation = when (this) {
    CameraOrientation.UP -> AVCaptureVideoOrientationPortrait
    CameraOrientation.DOWN -> AVCaptureVideoOrientationPortraitUpsideDown
    CameraOrientation.LEFT -> AVCaptureVideoOrientationLandscapeRight
    CameraOrientation.RIGHT -> AVCaptureVideoOrientationLandscapeLeft
}

/** Converts a [UIInterfaceOrientation] to a [CameraOrientation]. `unknown` is treated like `portrait`. */
internal fun cameraOrientationOfInterface(orientation: UIInterfaceOrientation): CameraOrientation =
    when (orientation) {
        UIInterfaceOrientationPortraitUpsideDown -> CameraOrientation.DOWN
        UIInterfaceOrientationLandscapeLeft -> CameraOrientation.RIGHT
        UIInterfaceOrientationLandscapeRight -> CameraOrientation.LEFT
        UIInterfaceOrientationPortrait -> CameraOrientation.UP
        else -> CameraOrientation.UP
    }

/** Converts a [PreviewResizeMode] to an `AVLayerVideoGravity`. */
internal fun PreviewResizeMode.toAVLayerVideoGravity(): String? = when (this) {
    PreviewResizeMode.COVER -> AVLayerVideoGravityResizeAspectFill
    PreviewResizeMode.CONTAIN -> AVLayerVideoGravityResizeAspect
}
