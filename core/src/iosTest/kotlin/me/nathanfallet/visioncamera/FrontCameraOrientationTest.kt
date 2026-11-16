package me.nathanfallet.visioncamera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGPointApplyAffineTransform
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIColor
import platform.CoreImage.CIImage
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.UIKit.UIImageOrientation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Frames, photos and depth data must be oriented the way SkiaCamera draws them: rotate the buffer so that its top is up
 * ([CameraOrientation.LEFT]: the content's top is on the left, so it is turned 90° clockwise), then mirror it.
 *
 * On an iPhone 11, the front camera was drawn upside down by SkiaCamera (its `landscapeLeft` connection was read as
 * [CameraOrientation.RIGHT]) and `Frame.toImage()` turned the back camera's frames upside down (its
 * [CameraOrientation.LEFT] buffers were tagged `UIImageOrientationLeft` instead of `UIImageOrientationRight`).
 */
@OptIn(ExperimentalForeignApi::class)
class FrontCameraOrientationTest {
    private val combinations = CameraOrientation.entries.flatMap { listOf(it to false, it to true) }
    private val corners = listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(0.0, 1.0), Point(1.0, 1.0), Point(0.25, 0.5))

    /** Where the normalized buffer [point] (y down) is displayed, the way SkiaCamera draws a frame. */
    private fun displayed(point: Point, orientation: CameraOrientation, isMirrored: Boolean): Point {
        val upright = when (orientation) {
            CameraOrientation.UP -> point
            CameraOrientation.LEFT -> Point(1 - point.y, point.x)
            CameraOrientation.RIGHT -> Point(point.y, 1 - point.x)
            CameraOrientation.DOWN -> Point(1 - point.x, 1 - point.y)
        }
        return if (isMirrored) Point(1 - upright.x, upright.y) else upright
    }

    private fun assertClose(expected: Point, actual: Point, message: String) {
        assertTrue(abs(expected.x - actual.x) < EPSILON && abs(expected.y - actual.y) < EPSILON, "$message: $actual")
    }

    @Test
    fun cgOrientationOrientsLikeSkiaCamera() {
        // Core Image applies an EXIF orientation the way UIKit and every image viewer display it.
        val width = 4.0
        val height = 2.0
        val image = CIImage.imageWithColor(CIColor.colorWithRed(1.0, 0.0, 0.0))
            .imageByCroppingToRect(CGRectMake(0.0, 0.0, width, height))
        for ((orientation, isMirrored) in combinations) {
            val transform = image.imageTransformForCGOrientation(orientation.toCGOrientation(isMirrored))
            val extent = CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), transform)
            for (corner in corners) {
                // Core Image's origin is at the bottom left.
                val bottomUp = CGPointMake(corner.x * width, (1 - corner.y) * height)
                val actual = CGPointApplyAffineTransform(bottomUp, transform).useContents { Point(x, y) }
                val normalized = extent.useContents {
                    Point((actual.x - origin.x) / size.width, 1 - (actual.y - origin.y) / size.height)
                }
                assertClose(displayed(corner, orientation, isMirrored), normalized, "$orientation mirrored=$isMirrored")
            }
        }
    }

    @Test
    fun uiImageOrientationMatchesTheExifOrientation() {
        for ((orientation, isMirrored) in combinations) {
            assertEquals(
                uiImageOrientationOfExif(orientation.toCGOrientation(isMirrored)),
                orientation.toUIImageOrientation(isMirrored),
                "$orientation mirrored=$isMirrored",
            )
        }
    }

    @Test
    fun backCameraBuffersAreTaggedRight() {
        assertEquals(UIImageOrientation.UIImageOrientationRight, CameraOrientation.LEFT.toUIImageOrientation())
        assertEquals(
            UIImageOrientation.UIImageOrientationLeftMirrored,
            CameraOrientation.LEFT.toUIImageOrientation(isMirrored = true),
        )
    }

    @Test
    fun uiImageOrientationRoundTrips() {
        for ((orientation, isMirrored) in combinations) {
            val uiOrientation = orientation.toUIImageOrientation(isMirrored)
            assertEquals(orientation, cameraOrientationOf(uiOrientation), "$orientation mirrored=$isMirrored")
            assertEquals(isMirrored, uiOrientation.isMirrored, "$orientation mirrored=$isMirrored")
        }
    }

    @Test
    fun portraitPhotosHaveTheOrientationOfAndroid() {
        // EXIF 6 is a back camera portrait photo (Android: 90° -> LEFT), EXIF 5 a mirrored front camera one.
        assertEquals(CameraOrientation.LEFT, cameraOrientationOf(uiImageOrientationOfExif(6u)))
        assertEquals(CameraOrientation.LEFT, cameraOrientationOf(uiImageOrientationOfExif(5u)))
        assertEquals(CameraOrientation.RIGHT, cameraOrientationOf(uiImageOrientationOfExif(8u)))
    }

    @Test
    fun frameToCameraMatrixMapsLikeSkiaCamera() {
        val width = 4uL
        val height = 2uL
        val pixelBuffer = memScoped {
            val out = alloc<CVPixelBufferRefVar>()
            CVPixelBufferCreate(null, width, height, kCVPixelFormatType_32BGRA, null, out.ptr)
            checkNotNull(out.value)
        }
        try {
            for ((orientation, isMirrored) in combinations) {
                val matrix = FrameCoordinateSystemConverter.getFrameToCameraMatrix(pixelBuffer, orientation, isMirrored)
                for (corner in corners) {
                    val actual = Point(corner.x * width.toDouble(), corner.y * height.toDouble()).applying(matrix)
                    assertClose(displayed(corner, orientation, isMirrored), actual, "$orientation mirrored=$isMirrored")
                }
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }

    @Test
    fun frontCameraLandscapeLeftBuffersAreLeft() {
        assertEquals(
            CameraOrientation.LEFT,
            bufferOrientationOf(CameraOrientation.RIGHT, isFrontCamera = true, isVideoMirrored = false),
        )
        assertEquals(
            CameraOrientation.RIGHT,
            bufferOrientationOf(CameraOrientation.LEFT, isFrontCamera = true, isVideoMirrored = false),
        )
    }

    @Test
    fun backCameraAndMirroredConnectionsKeepTheirOrientation() {
        for (orientation in CameraOrientation.entries) {
            assertEquals(orientation, bufferOrientationOf(orientation, isFrontCamera = false, isVideoMirrored = false))
            assertEquals(orientation, bufferOrientationOf(orientation, isFrontCamera = true, isVideoMirrored = true))
        }
    }

    @Test
    fun portraitFrontCameraBuffersKeepTheirOrientation() {
        assertEquals(CameraOrientation.UP, bufferOrientationOf(CameraOrientation.UP, true, false))
        assertEquals(CameraOrientation.DOWN, bufferOrientationOf(CameraOrientation.DOWN, true, false))
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
