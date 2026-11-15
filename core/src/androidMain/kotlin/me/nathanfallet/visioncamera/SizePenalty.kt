package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import kotlin.math.abs
import kotlin.math.ln

// Port of V5's Sizes+sortedByClosestTo.kt and Size+toQualitySelector.kt.

/** Maximum relative difference in aspect ratio to still be considered a "match". */
private const val ASPECT_RATIO_TOLERANCE = 0.02

/**
 * Calibrated so a 4:3 to 16:9 mismatch costs ~1.0 (about one Quality bucket on the log scale). Values above ~10 let
 * aspect dominate pixel proximity enough that a 12MP 4:3 request gets answered with SD instead of UHD.
 */
private const val ASPECT_MISMATCH_WEIGHT = 3.0

/** The longer edge of this size (orientation-independent). */
private val android.util.Size.long: Int get() = maxOf(width, height)

/** The shorter edge of this size (orientation-independent). */
private val android.util.Size.short: Int get() = minOf(width, height)

/** Converts a [Size] to an Android [android.util.Size]. */
internal fun Size.toAndroidSize(): android.util.Size = android.util.Size(width, height)

/**
 * Computes a comparable penalty score between this size and [target].
 *
 * Combines a log-ratio pixel-count distance (scale-invariant: 2x bigger and 2x smaller are equidistant) and a
 * weighted aspect-ratio mismatch. Aspect within [ASPECT_RATIO_TOLERANCE] is free; outside, it is scaled by
 * [ASPECT_MISMATCH_WEIGHT] so aspect biases the ranking without overriding large pixel-count gaps.
 */
internal fun android.util.Size.penalty(target: android.util.Size): Double {
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

/** Sorts these sizes by [penalty] to [targetSize] (ascending). */
internal fun List<android.util.Size>.sortedByClosestTo(targetSize: android.util.Size): List<android.util.Size> =
    sortedBy { it.penalty(targetSize) }

/**
 * Creates a [QualitySelector] trying every CameraX [Quality] tier, ordered by how close its typical sizes are to
 * this target size.
 */
@SuppressLint("RestrictedApi")
internal fun Size.toQualitySelector(): QualitySelector {
    val targetSize = toAndroidSize()
    val sorted = Quality.getSortedQualities().sortedBy { quality ->
        if (quality is Quality.ConstantQuality) {
            // Find the typical size closest to our target for this Quality tier, reusing the same
            // aspect-ratio-first + log-ratio ranking as sortedByClosestTo.
            quality.typicalSizes.minOfOrNull { it.penalty(targetSize) } ?: Double.MAX_VALUE
        } else {
            Log.w(TAG, "Quality $quality is not a ConstantQuality - cannot determine its Resolution...")
            Double.MAX_VALUE
        }
    }
    return QualitySelector.fromOrderedList(sorted)
}
