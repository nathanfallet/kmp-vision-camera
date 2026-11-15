package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGAffineTransformInvert
import platform.CoreGraphics.CGAffineTransformRotate
import platform.CoreGraphics.CGAffineTransformScale
import platform.CoreGraphics.CGAffineTransformTranslate
import platform.CoreGraphics.CGPointApplyAffineTransform
import platform.CoreGraphics.CGPointMake
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import kotlin.math.PI

/**
 * Converts points between a [Frame]'s (or a [Depth]'s) coordinate system and the Camera's normalized coordinate
 * system (port of V5's `Utils/FrameCoordinateSystemConverter.swift`).
 */
internal object FrameCoordinateSystemConverter {
    /**
     * Get a Matrix that can convert a point in the given [pixelBuffer] to normalized Camera coordinates
     * (`(cx, cy) ∈ [0, 1]²`). The [orientation] and [isMirrored] flags affect the Matrix if the `Frame` needs those to
     * be adjusted.
     */
    fun getFrameToCameraMatrix(
        pixelBuffer: CVPixelBufferRef,
        orientation: CameraOrientation,
        isMirrored: Boolean,
    ): CValue<CGAffineTransform> {
        var matrix = CGAffineTransformIdentity.readValue()

        // 1. Counter-rotate by the orientation to get it up-right
        matrix = when (orientation) {
            CameraOrientation.UP -> matrix
            CameraOrientation.DOWN -> CGAffineTransformRotate(CGAffineTransformTranslate(matrix, 1.0, 1.0), PI)
            CameraOrientation.LEFT -> CGAffineTransformRotate(CGAffineTransformTranslate(matrix, 1.0, 0.0), PI / 2)
            CameraOrientation.RIGHT -> CGAffineTransformRotate(CGAffineTransformTranslate(matrix, 0.0, 1.0), -PI / 2)
        }

        // 2. If the Frame is mirrored, counter-mirror our Matrix
        if (isMirrored) {
            val mirror = CGAffineTransformScale(
                CGAffineTransformTranslate(CGAffineTransformIdentity.readValue(), 1.0, 0.0),
                -1.0,
                1.0,
            )
            matrix = CGAffineTransformConcat(mirror, matrix)
        }

        // 3. Our Matrix is in [0, 1], so let's scale it to [0, width|height] now
        val width = CVPixelBufferGetWidth(pixelBuffer).toDouble()
        val height = CVPixelBufferGetHeight(pixelBuffer).toDouble()
        return CGAffineTransformScale(matrix, 1 / width, 1 / height)
    }

    /** The inverse of [getFrameToCameraMatrix]: converts normalized Camera coordinates to the [pixelBuffer]'s. */
    fun getCameraToFrameMatrix(
        pixelBuffer: CVPixelBufferRef,
        orientation: CameraOrientation,
        isMirrored: Boolean,
    ): CValue<CGAffineTransform> =
        CGAffineTransformInvert(getFrameToCameraMatrix(pixelBuffer, orientation, isMirrored))
}

/** Applies the given affine transform [matrix] to this [Point]. */
internal fun Point.applying(matrix: CValue<CGAffineTransform>): Point =
    CGPointApplyAffineTransform(CGPointMake(x, y), matrix).useContents { Point(x = x, y = y) }
