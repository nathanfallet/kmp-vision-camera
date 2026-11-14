package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureAutoFocusSystem
import platform.AVFoundation.AVCaptureAutoFocusSystemContrastDetection
import platform.AVFoundation.AVCaptureAutoFocusSystemNone
import platform.AVFoundation.AVCaptureAutoFocusSystemPhaseDetection
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVFrameRateRange

// Port of V5's Constraints/ResolvableConstraint/*.swift: how each constraint is resolved against a single
// `AVCaptureDevice.Format`, returning a penalty (how far from ideal), and the actual achievable value if any.
// Stabilization modes and dynamic ranges are resolved in their own files.

// region FPS

/** Whether this frame rate range contains the given [fps]. */
internal fun AVFrameRateRange.contains(fps: Double): Boolean = fps in minFrameRate..maxFrameRate

/** The distance between the given [fps] and this frame rate range, or `0` if it is contained. */
internal fun AVFrameRateRange.penalty(fps: Double): Double =
    if (contains(fps)) 0.0 else maxOf(minFrameRate - fps, fps - maxFrameRate)

/** Resolves this [FPSConstraint] to the nearest supported FPS of the given [format]. */
internal fun FPSConstraint.resolve(format: AVCaptureDeviceFormat): ConstraintResolution<Double> {
    val nearestRange = format.frameRateRanges.minByOrNull { it.penalty(fps) }
        ?: throw VisionCameraException("Format $format does not have any frame rate ranges!")
    val clampedFPS = fps.coerceIn(nearestRange.minFrameRate, nearestRange.maxFrameRate)
    return ConstraintResolution(penalty = nearestRange.penalty(fps), resolvedValue = clampedFPS)
}

// endregion

// region Resolution Bias

/**
 * Resolves this [ResolutionBiasConstraint] against the given [format], returning a penalty for how far the format's
 * resolutions are from the biased output's target resolution.
 */
internal fun ResolutionBiasConstraint.resolve(format: AVCaptureDeviceFormat): Double {
    val participant = resolutionBias as? ResolutionNegotiationParticipant
        ?: throw VisionCameraException(
            "The given CameraOutput (in `resolutionBias`) does not conform to `ResolutionNegotiationParticipant`!",
        )
    val streamType = participant.streamType
    val supportedResolutions = format.supportedResolutions(streamType)
    val penalty = when (val targetResolution = participant.targetResolution) {
        is ResolutionRule.ClosestTo ->
            supportedResolutions.nearest(targetResolution.size)?.penalty(targetResolution.size)
        is ResolutionRule.Min ->
            supportedResolutions.minOfOrNull { it.penaltyCoveringAtLeast(targetResolution.size) }
        ResolutionRule.Any -> 0.0
    }
    return penalty ?: throw VisionCameraException(
        "Format $format does not have any available resolutions for stream type \"$streamType\"!",
    )
}

// endregion

// region Pixel Format & Binning

/** Resolves this [PixelFormatConstraint] against the given [format]: `0` if the pixel formats match, `1` otherwise. */
internal fun PixelFormatConstraint.resolve(format: AVCaptureDeviceFormat): Double =
    if (format.pixelFormat == pixelFormat) 0.0 else 1.0

/** Resolves this [BinnedConstraint] against the given [format]. */
internal fun BinnedConstraint.resolve(format: AVCaptureDeviceFormat): Double = when {
    format.videoBinned == binned -> 0.0
    format.videoBinned -> 1.0
    else -> 2.0
}

// endregion

// region Auto-Focus System

/** The penalty of using a format with the [actual] auto-focus system when the [target] one is preferred. */
@Suppress("MagicNumber")
internal fun autoFocusSystemPenalty(target: AVCaptureAutoFocusSystem, actual: AVCaptureAutoFocusSystem): Double {
    val known = listOf(
        AVCaptureAutoFocusSystemNone,
        AVCaptureAutoFocusSystemContrastDetection,
        AVCaptureAutoFocusSystemPhaseDetection,
    )
    return when {
        target !in known || actual !in known -> 4.0
        target == actual -> 0.0
        // Contrast vs. phase detection
        target != AVCaptureAutoFocusSystemNone && actual != AVCaptureAutoFocusSystemNone -> 1.0
        // No auto-focus wanted, but the format has one
        target == AVCaptureAutoFocusSystemNone -> 2.0
        // Auto-focus wanted, but the format has none
        else -> 3.0
    }
}

// endregion
