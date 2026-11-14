package me.nathanfallet.visioncamera

/**
 * Constraints are session configuration options that are negotiated by the [CameraSession] to find a
 * closest-matching working Camera configuration.
 *
 * In other words, Constraints describe _intent_. They are ranked by priority in the order they are passed,
 * descending: the first item has the highest priority.
 *
 * ```kotlin
 * val constraints = listOf(
 *     ResolutionBiasConstraint(photoOutput),
 *     PhotoHDRConstraint(true),
 *     ResolutionBiasConstraint(videoOutput),
 * )
 * ```
 *
 * @see CameraSessionConnection.constraints
 */
sealed interface Constraint

/**
 * A constraint to set a given [fps] for any Video, Frame or Preview Streams.
 */
data class FPSConstraint(
    /** The target frame rate, in frames per second. */
    val fps: Double,
) : Constraint

/**
 * A constraint to set a [TargetStabilizationMode] for Video Streams.
 */
data class VideoStabilizationModeConstraint(
    /** The target stabilization mode. */
    val videoStabilizationMode: TargetStabilizationMode,
) : Constraint

/**
 * A constraint to set a [TargetStabilizationMode] for Preview Streams.
 */
data class PreviewStabilizationModeConstraint(
    /** The target stabilization mode. */
    val previewStabilizationMode: TargetStabilizationMode,
) : Constraint

/**
 * A constraint to bias the resolution of the given [CameraOutput] over other constraints.
 *
 * The resolution bias goes both ways; if the given [CameraOutput]'s resolution is very low, a Camera
 * configuration most closely matching that low resolution will be used.
 */
data class ResolutionBiasConstraint(
    /** The output whose resolution should be favoured. */
    val resolutionBias: CameraOutput,
) : Constraint

/**
 * A constraint to set a [TargetDynamicRange] for Video Streams.
 */
data class VideoDynamicRangeConstraint(
    /** The target dynamic range. */
    val videoDynamicRange: TargetDynamicRange,
) : Constraint

/**
 * A constraint to set Photo HDR for Photo Outputs.
 */
data class PhotoHDRConstraint(
    /** Whether Photo HDR should be enabled. */
    val photoHDR: Boolean,
) : Constraint

/**
 * A constraint to prefer a streaming Format with the given target [PixelFormat].
 */
data class PixelFormatConstraint(
    /** The preferred pixel format. */
    val pixelFormat: PixelFormat,
) : Constraint

/**
 * A constraint to prefer a binned format, or prefer a non-binned format.
 *
 * Pixel binning combines multiple neighboring sensor pixels into one larger effective pixel, which improves
 * low-light sensitivity and performance at the cost of fine detail.
 */
data class BinnedConstraint(
    /** Whether a binned format should be preferred. */
    val binned: Boolean,
) : Constraint
