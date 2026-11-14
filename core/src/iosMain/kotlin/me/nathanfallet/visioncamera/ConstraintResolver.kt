package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureAutoFocusSystem
import platform.AVFoundation.AVCaptureAutoFocusSystemPhaseDetection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.multiCamSupported

// Port of V5's Constraints/ConstraintResolver.swift, NegotiatedFormat.swift, EnabledConstraints.swift,
// ResolvedConstraints.swift and InternalConstraint.swift.

/** The negotiated [format] (and [depthFormat], if depth is needed) of a resolved configuration. */
internal data class NegotiatedFormat(
    val format: AVCaptureDeviceFormat,
    val depthFormat: AVCaptureDeviceFormat?,
)

/** The settings enabled by a resolved configuration. */
internal data class EnabledConstraints(
    val selectedFPS: Double?,
    val selectedVideoStabilizationMode: TargetStabilizationMode?,
    val selectedPreviewStabilizationMode: TargetStabilizationMode?,
    val selectedVideoDynamicRange: TargetDynamicRange?,
)

/** The result of resolving [Constraint]s for a [device]. */
internal data class ResolvedConstraints(
    val device: AVCaptureDevice,
    val negotiatedFormat: NegotiatedFormat,
    val enabledConstraints: EnabledConstraints,
)

/** A constraint used during negotiation: either a user [Constraint], or an inferred internal one. */
internal sealed interface InternalConstraint {
    /** A constraint passed by the user. */
    data class UserConstraint(val constraint: Constraint) : InternalConstraint

    /** Prefer formats with the given auto-focus system. */
    data class PreferredAutoFocusSystem(val autoFocusSystem: AVCaptureAutoFocusSystem) : InternalConstraint

    /** Prefer formats supporting the highest photo quality. */
    data object PreferHighestPhotoQuality : InternalConstraint

    /** Prefer formats supporting high photo quality prioritization. */
    data object PreferHighQualityPrioritization : InternalConstraint
}

/**
 * Type-erased resolved value, so we can accumulate heterogeneous constraint results into a single
 * [EnabledConstraints].
 */
internal sealed interface ResolvedConstraintValue {
    data class FPS(val value: Double) : ResolvedConstraintValue

    data class VideoStabilizationMode(val value: TargetStabilizationMode) : ResolvedConstraintValue

    data class PreviewStabilizationMode(val value: TargetStabilizationMode) : ResolvedConstraintValue

    data class VideoDynamicRange(val value: TargetDynamicRange) : ResolvedConstraintValue

    /** The constraint only affects format selection, not enabled settings. */
    data object FormatOnly : ResolvedConstraintValue
}

/**
 * The result of resolving a constraint against a format. Contains both the [penalty] (for format scoring) and the
 * [resolvedValue] (for enabled constraints), computed in a single pass.
 */
internal data class ConstraintResolution<T>(
    val penalty: Double,
    val resolvedValue: T,
)

/** A constraint evaluation result: penalty + type-erased resolved value. */
internal data class ConstraintEvaluation(
    val penalty: Double,
    val resolved: ResolvedConstraintValue,
)

/** Result of evaluating all constraints against a single format. */
private class FormatEvaluation(
    val format: AVCaptureDeviceFormat,
    val totalPenalty: Double,
    val enabledConstraints: EnabledConstraints,
)

/**
 * Resolves [Constraint]s into the best matching format and enabled settings of an [AVCaptureDevice].
 *
 * Every format of the device is evaluated against all constraints, each constraint contributing a penalty weighted by
 * its priority (the first constraint has the highest weight). The format with the lowest total penalty wins.
 */
internal object ConstraintResolver {
    /**
     * Resolves the given [constraints] for the given [device], streaming to the given [outputs].
     *
     * @throws VisionCameraException If no format of the device can stream to the given outputs.
     */
    fun resolveConstraints(
        device: CameraDeviceOrPosition,
        constraints: List<Constraint>,
        outputs: List<CameraOutput>,
        isMultiCam: Boolean,
    ): ResolvedConstraints {
        val captureDevice = resolveCaptureDevice(device)
        val nativeOutputs = outputs.mapNotNull { output ->
            when (output) {
                is NativeCameraOutput -> output
                is NativePreviewViewOutput -> null
                else -> throw VisionCameraException(
                    "Connection output \"$output\" is not of type `NativeCameraOutput` or `NativePreviewViewOutput`!",
                )
            }
        }
        return resolveConstraints(captureDevice, constraints, nativeOutputs, isMultiCam)
    }

    private fun resolveConstraints(
        device: AVCaptureDevice,
        userConstraints: List<Constraint>,
        outputs: List<NativeCameraOutput>,
        isMultiCam: Boolean,
    ): ResolvedConstraints {
        // Build the full constraint list: user constraints first (highest priority),
        // then inferred internal constraints (lowest priority).
        val constraints = buildList {
            addAll(userConstraints.map { InternalConstraint.UserConstraint(it) })
            add(InternalConstraint.PreferredAutoFocusSystem(AVCaptureAutoFocusSystemPhaseDetection))
            if (outputs.any { it.streamType == StreamType.PHOTO }) {
                add(InternalConstraint.PreferHighQualityPrioritization)
                add(InternalConstraint.PreferHighestPhotoQuality)
            }
        }

        // Hard requirements: filter out formats that are not possible at all.
        val formats = filterFormats(device.deviceFormats, outputs, isMultiCam)

        // Evaluate all formats and pick the one with the lowest total penalty.
        // This is a single pass - format selection and enabled constraints
        // are computed together from the same resolve() calls.
        val best = formats.map { evaluate(it, constraints) }.minByOrNull { it.totalPenalty }
            ?: throw VisionCameraException("The given `device` does not have any `formats`!")
        Logger.info("Selected Format: ${best.format}")

        // If we need depth data, also find the best depth format.
        // Like V5, we just choose the first depth format: it is not negotiated yet.
        val needsDepth = outputs.any { it.requiresDepthFormat }
        val bestDepthFormat = if (needsDepth) best.format.depthDataFormats.firstOrNull() else null

        return ResolvedConstraints(
            device = device,
            negotiatedFormat = NegotiatedFormat(format = best.format, depthFormat = bestDepthFormat),
            enabledConstraints = best.enabledConstraints,
        )
    }

    /**
     * Evaluates all constraints against a single format in one pass. Returns the format, its total weighted penalty,
     * and the enabled constraints that would apply if this format is selected.
     */
    private fun evaluate(format: AVCaptureDeviceFormat, constraints: List<InternalConstraint>): FormatEvaluation {
        var totalPenalty = 0.0

        // First-match-wins accumulators (priority order = list order)
        var fps: Double? = null
        var videoStabilizationMode: TargetStabilizationMode? = null
        var previewStabilizationMode: TargetStabilizationMode? = null
        var videoDynamicRange: TargetDynamicRange? = null

        constraints.forEachIndexed { index, constraint ->
            val weight = (constraints.size - index).toDouble()
            val evaluation = constraint.evaluate(format)
            totalPenalty += evaluation.penalty * weight

            // Accumulate enabled values - first of each type wins (= highest priority)
            when (val resolved = evaluation.resolved) {
                is ResolvedConstraintValue.FPS -> fps = fps ?: resolved.value
                is ResolvedConstraintValue.VideoStabilizationMode ->
                    videoStabilizationMode = videoStabilizationMode ?: resolved.value
                is ResolvedConstraintValue.PreviewStabilizationMode ->
                    previewStabilizationMode = previewStabilizationMode ?: resolved.value
                is ResolvedConstraintValue.VideoDynamicRange -> videoDynamicRange = videoDynamicRange ?: resolved.value
                ResolvedConstraintValue.FormatOnly -> Unit
            }
        }

        return FormatEvaluation(
            format = format,
            totalPenalty = totalPenalty,
            enabledConstraints = EnabledConstraints(
                selectedFPS = fps,
                selectedVideoStabilizationMode = videoStabilizationMode,
                selectedPreviewStabilizationMode = previewStabilizationMode,
                selectedVideoDynamicRange = videoDynamicRange,
            ),
        )
    }

    /**
     * Filters out formats that we cannot support at all. These are hard requirements, not soft constraints/biases.
     */
    @Suppress("ThrowsCount")
    private fun filterFormats(
        allFormats: List<AVCaptureDeviceFormat>,
        outputs: List<NativeCameraOutput>,
        isMultiCam: Boolean,
    ): List<AVCaptureDeviceFormat> {
        var formats = allFormats
        if (formats.isEmpty()) throw VisionCameraException("The given `device` does not have any `formats`!")

        // 1. If we need a Depth Output, filter out formats without depth support
        if (outputs.any { it.requiresDepthFormat }) {
            formats = formats.filter { it.depthDataFormats.isNotEmpty() }
            if (formats.isEmpty()) {
                throw VisionCameraException(
                    "The given `device` does not have any `formats` that have `supportedDepthDataFormats`. " +
                        "Does this device support depth?",
                )
            }
        }

        // 2. If multi-cam, ensure formats support it
        if (isMultiCam) {
            formats = formats.filter { it.multiCamSupported }
            if (formats.isEmpty()) {
                throw VisionCameraException(
                    "The given `device` does not have any `formats` that support multi-cam sessions! " +
                        "Does this `device` support multi-cam?",
                )
            }
        }

        // 3. Ensure formats support all target outputs
        formats = formats.filter { format -> outputs.all { format.supportsOutput(it.output) } }
        if (formats.isEmpty()) {
            throw VisionCameraException(
                "The given `device` does not have any `formats` that support the attached Outputs!",
            )
        }

        return formats
    }
}

/** Evaluates this constraint against the given [format]. */
internal fun InternalConstraint.evaluate(format: AVCaptureDeviceFormat): ConstraintEvaluation = when (this) {
    is InternalConstraint.UserConstraint -> constraint.evaluate(format)
    is InternalConstraint.PreferredAutoFocusSystem -> ConstraintEvaluation(
        penalty = autoFocusSystemPenalty(target = autoFocusSystem, actual = format.autoFocusSystem),
        resolved = ResolvedConstraintValue.FormatOnly,
    )
    InternalConstraint.PreferHighestPhotoQuality -> ConstraintEvaluation(
        penalty = if (format.highestPhotoQualitySupported) 0.0 else 1.0,
        resolved = ResolvedConstraintValue.FormatOnly,
    )
    InternalConstraint.PreferHighQualityPrioritization -> ConstraintEvaluation(
        penalty = if (format.highPhotoQualitySupported) 0.0 else 1.0,
        resolved = ResolvedConstraintValue.FormatOnly,
    )
}

/** Evaluates this user constraint against the given [format]. */
internal fun Constraint.evaluate(format: AVCaptureDeviceFormat): ConstraintEvaluation = when (this) {
    is FPSConstraint -> resolve(format).let {
        ConstraintEvaluation(it.penalty, ResolvedConstraintValue.FPS(it.resolvedValue))
    }
    is VideoStabilizationModeConstraint -> videoStabilizationMode.resolve(format).let {
        ConstraintEvaluation(it.penalty, ResolvedConstraintValue.VideoStabilizationMode(it.resolvedValue))
    }
    is PreviewStabilizationModeConstraint -> previewStabilizationMode.resolve(format).let {
        ConstraintEvaluation(it.penalty, ResolvedConstraintValue.PreviewStabilizationMode(it.resolvedValue))
    }
    is ResolutionBiasConstraint -> ConstraintEvaluation(resolve(format), ResolvedConstraintValue.FormatOnly)
    is VideoDynamicRangeConstraint -> videoDynamicRange.resolve(format).let {
        ConstraintEvaluation(it.penalty, ResolvedConstraintValue.VideoDynamicRange(it.resolvedValue))
    }
    // Photo HDR is not supported on iOS.
    is PhotoHDRConstraint -> ConstraintEvaluation(0.0, ResolvedConstraintValue.FormatOnly)
    is PixelFormatConstraint -> ConstraintEvaluation(resolve(format), ResolvedConstraintValue.FormatOnly)
    is BinnedConstraint -> ConstraintEvaluation(resolve(format), ResolvedConstraintValue.FormatOnly)
}
