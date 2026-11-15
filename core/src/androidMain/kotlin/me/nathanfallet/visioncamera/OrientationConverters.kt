package me.nathanfallet.visioncamera

import android.view.Surface

private const val DEGREES_45 = 45
private const val DEGREES_90 = 90
private const val DEGREES_135 = 135
private const val DEGREES_180 = 180
private const val DEGREES_225 = 225
private const val DEGREES_270 = 270
private const val DEGREES_315 = 315
private const val DEGREES_360 = 360

/**
 * The `Surface.ROTATION_*` value of this [CameraOrientation], used as a CameraX target rotation.
 *
 * This is an extension point for plugin modules, e.g. to set the `targetRotation` of a [NativeCameraOutput]'s use
 * case when its [CameraOutput.outputOrientation] changes.
 */
val CameraOrientation.surfaceRotation: Int
    get() = when (this) {
        CameraOrientation.UP -> Surface.ROTATION_0
        CameraOrientation.DOWN -> Surface.ROTATION_180
        CameraOrientation.LEFT -> Surface.ROTATION_270
        CameraOrientation.RIGHT -> Surface.ROTATION_90
    }

/** The rotation of this [CameraOrientation], in degrees. */
internal val CameraOrientation.degrees: Int
    get() = when (this) {
        CameraOrientation.UP -> 0
        CameraOrientation.DOWN -> DEGREES_180
        CameraOrientation.LEFT -> DEGREES_270
        CameraOrientation.RIGHT -> DEGREES_90
    }

/** Converts a rotation in degrees (CameraX `rotationDegrees`, or an EXIF rotation) to a [CameraOrientation]. */
internal fun cameraOrientationFromDegrees(degrees: Int): CameraOrientation {
    val normalized = ((degrees % DEGREES_360) + DEGREES_360) % DEGREES_360
    return when (normalized) {
        in DEGREES_45..DEGREES_135 -> CameraOrientation.LEFT
        in DEGREES_135..DEGREES_225 -> CameraOrientation.DOWN
        in DEGREES_225..DEGREES_315 -> CameraOrientation.RIGHT
        else -> CameraOrientation.UP
    }
}

/**
 * Converts a `Surface.ROTATION_*` value to a [CameraOrientation].
 *
 * @throws VisionCameraException If [surfaceRotation] is not a valid `Surface.ROTATION_*` value.
 */
internal fun cameraOrientationFromSurfaceRotation(surfaceRotation: Int): CameraOrientation = when (surfaceRotation) {
    Surface.ROTATION_0 -> CameraOrientation.UP
    Surface.ROTATION_180 -> CameraOrientation.DOWN
    Surface.ROTATION_90 -> CameraOrientation.RIGHT
    Surface.ROTATION_270 -> CameraOrientation.LEFT
    else -> throw VisionCameraException("Invalid Surface Rotation value: $surfaceRotation")
}

/** The logical counter-orientation of this [CameraOrientation], aka flipping it. */
internal fun CameraOrientation.counterRotated(): CameraOrientation = when (this) {
    CameraOrientation.UP -> CameraOrientation.UP
    CameraOrientation.DOWN -> CameraOrientation.DOWN
    CameraOrientation.LEFT -> CameraOrientation.RIGHT
    CameraOrientation.RIGHT -> CameraOrientation.LEFT
}
