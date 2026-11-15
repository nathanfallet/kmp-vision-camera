package me.nathanfallet.visioncamera.barcode.extensions

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.barcode.Rect
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.UIKit.UIImageOrientation

/**
 * The pixel coordinate space barcodes are reported in, converting Vision's coordinates into it.
 *
 * Vision reports normalized coordinates (`[0, 1]²`) with a bottom-left origin, in the **oriented** (upright) image it
 * was given. ML Kit (and so V5) reports pixel coordinates with a top-left origin, relative to its input:
 * - for a `Frame`, the frame's own coordinate system: its (un-rotated) pixel buffer, which `Frame.orientation` and
 *   `Frame.isMirrored` describe, and which `Frame.convertFramePointToCameraPoint` expects. The [orientation] is then
 *   the frame's orientation, and [width]x[height] its buffer's size.
 * - for an `Image`, the image's coordinate system: its upright pixels (`Image.width`x`Image.height`). The
 *   [orientation] is then [UIImageOrientation.UIImageOrientationUp].
 */
internal class VisionCoordinateSpace(
    /** The orientation of the space's content, i.e. the transform from the space to the upright image. */
    val orientation: UIImageOrientation,
    /** The width of the space, in pixels. */
    val width: Double,
    /** The height of the space, in pixels. */
    val height: Double,
) {
    /** Converts a Vision point (normalized, bottom-left origin, upright) to this space. */
    fun convert(point: CValue<CGPoint>): Point = point.useContents { convert(x, y) }

    /** Converts a Vision point (normalized, bottom-left origin, upright) to this space. */
    fun convert(x: Double, y: Double): Point {
        // Bottom-left origin -> top-left origin
        val (sourceX, sourceY) = uprightToSource(x, 1.0 - y, orientation)
        return Point(x = sourceX * width, y = sourceY * height)
    }

    /** Converts a Vision rectangle (normalized, bottom-left origin, upright) to this space. */
    fun convert(rect: CValue<CGRect>): Rect = rect.useContents {
        // A rotation by a multiple of 90° and a mirroring keep a rectangle axis-aligned: convert two opposite corners.
        val first = convert(origin.x, origin.y)
        val second = convert(origin.x + size.width, origin.y + size.height)
        Rect(
            left = minOf(first.x, second.x),
            right = maxOf(first.x, second.x),
            top = minOf(first.y, second.y),
            bottom = maxOf(first.y, second.y),
        )
    }
}

/**
 * Converts a normalized point (top-left origin) of the upright image to the normalized point (top-left origin) of
 * the source image whose content has the given EXIF-like [orientation].
 *
 * The [orientation] describes how the source must be transformed to be displayed upright (e.g.
 * [UIImageOrientation.UIImageOrientationRight]: the source is rotated 90° clockwise, its top row becomes the right
 * column), so this applies the inverse transform.
 */
internal fun uprightToSource(x: Double, y: Double, orientation: UIImageOrientation): Pair<Double, Double> =
    when (orientation) {
        UIImageOrientation.UIImageOrientationUp -> x to y
        UIImageOrientation.UIImageOrientationUpMirrored -> (1.0 - x) to y
        UIImageOrientation.UIImageOrientationDown -> (1.0 - x) to (1.0 - y)
        UIImageOrientation.UIImageOrientationDownMirrored -> x to (1.0 - y)
        UIImageOrientation.UIImageOrientationLeftMirrored -> y to x
        UIImageOrientation.UIImageOrientationRight -> y to (1.0 - x)
        UIImageOrientation.UIImageOrientationRightMirrored -> (1.0 - y) to (1.0 - x)
        UIImageOrientation.UIImageOrientationLeft -> (1.0 - y) to x
        // The shared iOS metadata compilation sees UIImageOrientation as an expect enum
        else -> x to y
    }
