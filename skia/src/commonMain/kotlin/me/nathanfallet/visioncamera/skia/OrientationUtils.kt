package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.CameraOrientation

/**
 * Converts a [CameraOrientation] to its equivalent clockwise rotation angle in degrees.
 */
internal fun orientationToDegrees(orientation: CameraOrientation): Int = when (orientation) {
    CameraOrientation.UP -> 0
    CameraOrientation.DOWN -> HALF_ROTATION_DEGREES
    CameraOrientation.LEFT -> HALF_ROTATION_DEGREES + QUARTER_ROTATION_DEGREES
    CameraOrientation.RIGHT -> QUARTER_ROTATION_DEGREES
}

/**
 * Normalizes the given rotation in degrees into the range from `0` to `360` (not including `360`).
 */
internal fun normalizeRotationDegrees(degrees: Int): Int {
    val normalized = degrees % FULL_ROTATION_DEGREES
    return if (normalized < 0) normalized + FULL_ROTATION_DEGREES else normalized
}

private const val QUARTER_ROTATION_DEGREES = 90
private const val HALF_ROTATION_DEGREES = 180
private const val FULL_ROTATION_DEGREES = 360
