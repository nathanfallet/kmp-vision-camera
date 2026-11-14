package me.nathanfallet.visioncamera

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// Port of V5's CMVideoDimensions+penalty.swift.

/** Maximum relative difference in aspect ratio to still be considered a "match". */
private const val ASPECT_RATIO_TOLERANCE = 0.02

/** Weight multiplied by the aspect ratio difference when outside [ASPECT_RATIO_TOLERANCE]. */
private const val ASPECT_MISMATCH_WEIGHT = 100.0

/** The longer edge of this size (orientation-independent). */
internal val Size.long: Int get() = max(width, height)

/** The shorter edge of this size (orientation-independent). */
internal val Size.short: Int get() = min(width, height)

/**
 * Computes a comparable penalty score between this size and [target].
 *
 * Primary: sizes matching the target aspect ratio (within [ASPECT_RATIO_TOLERANCE]) are always preferred.
 * Secondary: among the same aspect-ratio group, sizes are ranked by log-ratio of pixel counts (scale-invariant,
 * treats 2x bigger and 2x smaller as equidistant).
 */
internal fun Size.penalty(target: Size): Double {
    val targetAspectRatio = target.long.toDouble() / target.short.toDouble()
    val actualAspectRatio = long.toDouble() / short.toDouble()
    val aspectRatioDiff = abs(actualAspectRatio - targetAspectRatio) / targetAspectRatio
    val aspectRatioPenalty =
        if (aspectRatioDiff < ASPECT_RATIO_TOLERANCE) 0.0 else ASPECT_MISMATCH_WEIGHT * aspectRatioDiff

    val targetPixels = target.width.toDouble() * target.height.toDouble()
    val actualPixels = width.toDouble() * height.toDouble()
    val logPixelDistance = abs(ln(actualPixels / targetPixels))

    return aspectRatioPenalty + logPixelDistance
}

/**
 * Computes a comparable penalty score for using this size when at least [minimum] pixels are desired in both
 * dimensions.
 *
 * Any size covering [minimum] entirely (`>=` in both dimensions) is a perfect match (`0.0`). A smaller size is
 * penalized by the factor it needs to be upscaled with to cover [minimum] (aspect-fill), on the same logarithmic
 * pixel-count scale as [penalty]. Aspect ratio is irrelevant here, as any size covering [minimum] can simply be
 * center-cropped to it.
 */
internal fun Size.penaltyCoveringAtLeast(minimum: Size): Double {
    val requiredUpscaleFactor = maxOf(
        minimum.long.toDouble() / long.toDouble(),
        minimum.short.toDouble() / short.toDouble(),
        1.0,
    )
    // The upscale factor applies to both dimensions, so the pixel-count deficit is its square.
    return ln(requiredUpscaleFactor * requiredUpscaleFactor)
}

/** The size in this list nearest to [target], or `null` if this list is empty. */
internal fun List<Size>.nearest(target: Size): Size? = minByOrNull { it.penalty(target) }
