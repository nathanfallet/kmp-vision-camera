package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.UIKit.UIImageOrientation
import kotlin.math.PI

// Port of V5's UI+CameraOrientation.swift, UIImageOrientation+exif.swift and CG+CameraOrientation.swift.

/** Converts this [CameraOrientation] to a [UIImageOrientation], mirrored if [isMirrored]. */
internal fun CameraOrientation.toUIImageOrientation(isMirrored: Boolean): UIImageOrientation = when (this) {
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
