package me.nathanfallet.visioncamera.compose

import me.nathanfallet.visioncamera.CameraOrientation

// Port of V5's utils/getUIRotation.ts.

private const val FULL_TURN = 360
private const val HALF_TURN = 180

/** The clockwise rotation of this orientation, in degrees. */
@Suppress("MagicNumber")
private fun CameraOrientation.toDegrees(): Int = when (this) {
    CameraOrientation.UP -> 0
    CameraOrientation.RIGHT -> 90
    CameraOrientation.DOWN -> 180
    CameraOrientation.LEFT -> 270
}

/**
 * Gets the signed rotation, in degrees, needed to keep UI elements upright relative to the Camera
 * [outputOrientation], given the [interfaceOrientation].
 *
 * The result is normalized to the shortest cardinal rotation (`-90`, `0`, `90`), with opposite orientations
 * represented as `180`.
 */
internal fun getUIRotation(outputOrientation: CameraOrientation, interfaceOrientation: CameraOrientation): Int {
    // Calculate difference, not overshooting 360°
    val rotation = (interfaceOrientation.toDegrees() - outputOrientation.toDegrees() + FULL_TURN) % FULL_TURN
    // Converts 270° to -90°
    return if (rotation > HALF_TURN) rotation - FULL_TURN else rotation
}
