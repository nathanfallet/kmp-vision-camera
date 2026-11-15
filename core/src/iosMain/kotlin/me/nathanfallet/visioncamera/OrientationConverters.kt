package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.ImageIO.CGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationDownMirrored
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationLeftMirrored
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationRightMirrored
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.ImageIO.kCGImagePropertyOrientationUpMirrored
import platform.UIKit.UIImageOrientation
import kotlin.math.PI

// Port of V5's UI+CameraOrientation.swift, UIImageOrientation+exif.swift, CG+CameraOrientation.swift and
// CameraOrientation+degrees.swift.

/**
 * Converts this [CameraOrientation] to a [UIImageOrientation], mirrored if [isMirrored].
 *
 * This is an extension point for plugin modules, e.g. to orient a [NativeFrame]'s buffer for Vision or ML Kit.
 */
fun CameraOrientation.toUIImageOrientation(isMirrored: Boolean = false): UIImageOrientation = when (this) {
    CameraOrientation.UP ->
        if (isMirrored) UIImageOrientation.UIImageOrientationUpMirrored else UIImageOrientation.UIImageOrientationUp
    CameraOrientation.DOWN ->
        if (isMirrored) UIImageOrientation.UIImageOrientationDownMirrored else UIImageOrientation.UIImageOrientationDown
    CameraOrientation.LEFT ->
        if (isMirrored) UIImageOrientation.UIImageOrientationLeftMirrored else UIImageOrientation.UIImageOrientationLeft
    CameraOrientation.RIGHT ->
        if (isMirrored) {
            UIImageOrientation.UIImageOrientationRightMirrored
        } else {
            UIImageOrientation.UIImageOrientationRight
        }
}

/** Converts a [UIImageOrientation] to a [CameraOrientation], ignoring mirroring. */
internal fun cameraOrientationOf(orientation: UIImageOrientation): CameraOrientation = when (orientation) {
    UIImageOrientation.UIImageOrientationUp, UIImageOrientation.UIImageOrientationUpMirrored -> CameraOrientation.UP
    UIImageOrientation.UIImageOrientationDown, UIImageOrientation.UIImageOrientationDownMirrored ->
        CameraOrientation.DOWN
    UIImageOrientation.UIImageOrientationLeft, UIImageOrientation.UIImageOrientationLeftMirrored ->
        CameraOrientation.LEFT
    // UIImageOrientationRight, UIImageOrientationRightMirrored (the shared iOS metadata sees an expect enum)
    else -> CameraOrientation.RIGHT
}

/** Whether this [UIImageOrientation] is mirrored. */
internal val UIImageOrientation.isMirrored: Boolean
    get() = when (this) {
        UIImageOrientation.UIImageOrientationUpMirrored,
        UIImageOrientation.UIImageOrientationDownMirrored,
        UIImageOrientation.UIImageOrientationLeftMirrored,
        UIImageOrientation.UIImageOrientationRightMirrored,
        -> true
        else -> false
    }

/**
 * Converts an EXIF orientation flag (`kCGImagePropertyOrientation`) to a [UIImageOrientation].
 *
 * @throws VisionCameraException If the flag is invalid.
 */
@Suppress("MagicNumber")
internal fun uiImageOrientationOfExif(value: UInt): UIImageOrientation = when (value) {
    1u -> UIImageOrientation.UIImageOrientationUp
    2u -> UIImageOrientation.UIImageOrientationUpMirrored
    3u -> UIImageOrientation.UIImageOrientationDown
    4u -> UIImageOrientation.UIImageOrientationDownMirrored
    5u -> UIImageOrientation.UIImageOrientationLeftMirrored
    6u -> UIImageOrientation.UIImageOrientationRight
    7u -> UIImageOrientation.UIImageOrientationRightMirrored
    8u -> UIImageOrientation.UIImageOrientationLeft
    else -> throw VisionCameraException("Invalid EXIF Orientation Flag: $value")
}

/** The [CGAffineTransform] rotating content by this [CameraOrientation]. */
internal val CameraOrientation.affineTransform: CValue<CGAffineTransform>
    get() = when (this) {
        CameraOrientation.UP -> CGAffineTransformIdentity.readValue()
        CameraOrientation.LEFT -> CGAffineTransformMakeRotation(PI / 2)
        CameraOrientation.DOWN -> CGAffineTransformMakeRotation(PI)
        CameraOrientation.RIGHT -> CGAffineTransformMakeRotation(-(PI / 2))
    }

/** Converts this [CameraOrientation] to a [CGImagePropertyOrientation] (EXIF orientation), mirrored if [isMirrored]. */
internal fun CameraOrientation.toCGOrientation(isMirrored: Boolean): CGImagePropertyOrientation = when (this) {
    CameraOrientation.UP -> if (isMirrored) kCGImagePropertyOrientationUpMirrored else kCGImagePropertyOrientationUp
    CameraOrientation.DOWN ->
        if (isMirrored) kCGImagePropertyOrientationDownMirrored else kCGImagePropertyOrientationDown
    CameraOrientation.LEFT ->
        if (isMirrored) kCGImagePropertyOrientationLeftMirrored else kCGImagePropertyOrientationLeft
    CameraOrientation.RIGHT ->
        if (isMirrored) kCGImagePropertyOrientationRightMirrored else kCGImagePropertyOrientationRight
}

/** The clockwise rotation of this [CameraOrientation], in degrees. */
@Suppress("MagicNumber")
internal val CameraOrientation.degrees: Int
    get() = when (this) {
        CameraOrientation.UP -> 0
        CameraOrientation.LEFT -> 270
        CameraOrientation.RIGHT -> 90
        CameraOrientation.DOWN -> 180
    }

/** Converts a rotation in [degrees] to the closest [CameraOrientation]. */
@Suppress("MagicNumber")
internal fun cameraOrientationOfDegrees(degrees: Int): CameraOrientation = when (degrees.mod(360)) {
    in 45 until 135 -> CameraOrientation.RIGHT
    in 135 until 225 -> CameraOrientation.DOWN
    in 225 until 315 -> CameraOrientation.LEFT
    else -> CameraOrientation.UP
}

/** Rotates this [CameraOrientation] by the given [orientation]. */
internal fun CameraOrientation.rotatedBy(orientation: CameraOrientation): CameraOrientation =
    cameraOrientationOfDegrees(degrees + orientation.degrees)

/** This [CameraOrientation], relative to the given [orientation]. */
internal fun CameraOrientation.relativeTo(orientation: CameraOrientation): CameraOrientation =
    cameraOrientationOfDegrees(degrees - orientation.degrees)
