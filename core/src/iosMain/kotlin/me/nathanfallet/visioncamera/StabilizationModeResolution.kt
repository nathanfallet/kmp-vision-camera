package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDeviceFormat

// Port of V5's ResolvableConstraint+StabilizationModeConstraint.swift.

/** The next best mode to try if this mode is not supported. */
private fun TargetStabilizationMode.nextBestMode(): TargetStabilizationMode = when (this) {
    TargetStabilizationMode.Off -> TargetStabilizationMode.Off
    TargetStabilizationMode.Auto -> TargetStabilizationMode.Auto
    StabilizationMode.STANDARD -> TargetStabilizationMode.Auto
    // Cinematic modes - don't fall back to low-latency modes
    StabilizationMode.CINEMATIC -> StabilizationMode.STANDARD
    StabilizationMode.CINEMATIC_EXTENDED -> StabilizationMode.CINEMATIC
    StabilizationMode.CINEMATIC_EXTENDED_ENHANCED -> StabilizationMode.CINEMATIC_EXTENDED
    // Low-latency modes - don't fall back to cinematic
    StabilizationMode.PREVIEW_OPTIMIZED -> StabilizationMode.STANDARD
    StabilizationMode.LOW_LATENCY -> StabilizationMode.PREVIEW_OPTIMIZED
}

/**
 * Walks the fallback chain until we find a mode the [format] supports. Returns both the penalty (number of fallback
 * steps) and the resolved mode.
 */
internal fun TargetStabilizationMode.resolve(
    format: AVCaptureDeviceFormat,
): ConstraintResolution<TargetStabilizationMode> {
    var penalty = 0
    var currentMode = this
    while (currentMode != TargetStabilizationMode.Off) {
        if (format.isVideoStabilizationModeSupported(currentMode.toAVCaptureVideoStabilizationMode())) break
        val nextMode = currentMode.nextBestMode()
        // `auto` falls back to itself in V5: if even `auto` is not supported, fall back to `off` instead of looping.
        currentMode = if (nextMode == currentMode) TargetStabilizationMode.Off else nextMode
        penalty += 1
    }
    return ConstraintResolution(penalty = penalty.toDouble(), resolvedValue = currentMode)
}
