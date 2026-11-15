package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Point

/**
 * Describes how a [Frame]'s pixel data is oriented relative to the target output's coordinate system.
 *
 * @see transformPoint
 */
data class Transform(
    /** The rotation of the source pixel data relative to the target output. */
    val orientation: CameraOrientation,
    /** Whether the source pixel data is mirrored along the vertical axis relative to the target output. */
    val isMirrored: Boolean,
)

/**
 * Transforms the given [normalizedPoint] (values ranging from `0...1`) using the given [transform]'s
 * [Transform.orientation] and [Transform.isMirrored].
 *
 * ```kotlin
 * // A tap in the top-left corner of an upright view, to the Camera's coordinate system
 * val cameraPoint = transformPoint(Point(0.0, 0.0), Transform(frame.orientation, frame.isMirrored))
 * ```
 */
fun transformPoint(normalizedPoint: Point, transform: Transform): Point {
    // Mirror alongside X first
    val mirrored = if (transform.isMirrored) Point(1 - normalizedPoint.x, normalizedPoint.y) else normalizedPoint
    // Now rotate to camera coordinate system
    return when (transform.orientation) {
        CameraOrientation.UP -> mirrored
        CameraOrientation.DOWN -> Point(1 - mirrored.x, 1 - mirrored.y)
        CameraOrientation.LEFT -> Point(mirrored.y, 1 - mirrored.x)
        CameraOrientation.RIGHT -> Point(1 - mirrored.y, mirrored.x)
    }
}
