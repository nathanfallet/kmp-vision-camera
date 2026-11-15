package me.nathanfallet.visioncamera.barcode

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import me.nathanfallet.visioncamera.barcode.extensions.ResolvedBarcodeFormat
import me.nathanfallet.visioncamera.barcode.extensions.VisionCoordinateSpace
import me.nathanfallet.visioncamera.barcode.extensions.resolveVisionFormat
import me.nathanfallet.visioncamera.barcode.extensions.toBarcodeFormats
import me.nathanfallet.visioncamera.barcode.extensions.uprightToSource
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisionBarcodeTest {
    // region Coordinates

    private fun assertPoint(expected: Pair<Double, Double>, actual: Pair<Double, Double>, message: String) {
        val matches = abs(expected.first - actual.first) < 1e-9 && abs(expected.second - actual.second) < 1e-9
        assertTrue(matches, "$message: expected $expected, got $actual")
    }

    @Test
    fun uprightToSourceInvertsTheExifOrientations() {
        // Where the upright top-left corner (0, 0) and the upright point (0.9, 0.2) are in the source.
        val expected = mapOf(
            UIImageOrientation.UIImageOrientationUp to listOf(0.0 to 0.0, 0.9 to 0.2),
            UIImageOrientation.UIImageOrientationUpMirrored to listOf(1.0 to 0.0, 0.1 to 0.2),
            UIImageOrientation.UIImageOrientationDown to listOf(1.0 to 1.0, 0.1 to 0.8),
            UIImageOrientation.UIImageOrientationDownMirrored to listOf(0.0 to 1.0, 0.9 to 0.8),
            // EXIF 5: the source's top row is the upright left column, its left column the upright top row.
            UIImageOrientation.UIImageOrientationLeftMirrored to listOf(0.0 to 0.0, 0.2 to 0.9),
            // EXIF 6: the source's top row is the upright right column, its left column the upright top row.
            UIImageOrientation.UIImageOrientationRight to listOf(0.0 to 1.0, 0.2 to 0.1),
            // EXIF 7: the source's top row is the upright right column, its left column the upright bottom row.
            UIImageOrientation.UIImageOrientationRightMirrored to listOf(1.0 to 1.0, 0.8 to 0.1),
            // EXIF 8: the source's top row is the upright left column, its left column the upright bottom row.
            UIImageOrientation.UIImageOrientationLeft to listOf(1.0 to 0.0, 0.8 to 0.9),
        )
        for ((orientation, points) in expected) {
            assertPoint(points[0], uprightToSource(0.0, 0.0, orientation), "$orientation (0, 0)")
            assertPoint(points[1], uprightToSource(0.9, 0.2, orientation), "$orientation (0.9, 0.2)")
        }
    }

    @Test
    fun coordinateSpaceConvertsVisionCoordinates() {
        // A 1920x1080 landscape buffer, shown upright in portrait (rotated 90° clockwise).
        val space = VisionCoordinateSpace(UIImageOrientation.UIImageOrientationRight, width = 1920.0, height = 1080.0)
        // The buffer's top row is the upright image's right column, so Vision's origin (the upright bottom-left
        // corner) is the buffer's bottom-right corner.
        val origin = space.convert(0.0, 0.0)
        assertEquals(1920.0, origin.x, 1e-9)
        assertEquals(1080.0, origin.y, 1e-9)
        // Upright u ∈ [0.25, 0.75], Vision y ∈ [0.5, 0.75] (so top-left origin v ∈ [0.25, 0.5])
        // -> buffer x = v ∈ [0.25, 0.5], buffer y = 1 - u ∈ [0.25, 0.75].
        val rect = space.convert(CGRectMake(0.25, 0.5, 0.5, 0.25))
        assertEquals(Rect(left = 480.0, right = 960.0, top = 270.0, bottom = 810.0), rect)
    }

    // endregion

    // region Formats

    @Test
    fun allFormatsAreTheFormatsOfV5() {
        assertEquals(
            BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }.toSet(),
            TargetBarcodeFormat.ALL_FORMATS.toBarcodeFormats().toSet(),
        )
        for (target in TargetBarcodeFormat.entries.filter { it != TargetBarcodeFormat.ALL_FORMATS }) {
            assertEquals(listOf(target.name), target.toBarcodeFormats().map { it.name })
        }
    }

    @Test
    fun upcAIsReportedLikeMLKit() {
        val upcA = "0036000291452"
        val ean13 = "4006381333931"
        val all = TargetBarcodeFormat.ALL_FORMATS.toBarcodeFormats().toSet()
        assertEquals(
            ResolvedBarcodeFormat(BarcodeFormat.UPC_A, "036000291452"),
            resolveVisionFormat(BarcodeFormat.EAN_13, upcA, all),
        )
        assertEquals(
            ResolvedBarcodeFormat(BarcodeFormat.UPC_A, "036000291452"),
            resolveVisionFormat(BarcodeFormat.EAN_13, upcA, setOf(BarcodeFormat.UPC_A)),
        )
        assertEquals(
            ResolvedBarcodeFormat(BarcodeFormat.EAN_13, upcA),
            resolveVisionFormat(BarcodeFormat.EAN_13, upcA, setOf(BarcodeFormat.EAN_13)),
        )
        assertEquals(
            ResolvedBarcodeFormat(BarcodeFormat.EAN_13, ean13),
            resolveVisionFormat(BarcodeFormat.EAN_13, ean13, all),
        )
        // A real EAN-13 is not a UPC-A
        assertNull(resolveVisionFormat(BarcodeFormat.EAN_13, ean13, setOf(BarcodeFormat.UPC_A)))
        // Formats that were not requested are dropped
        assertNull(resolveVisionFormat(BarcodeFormat.QR_CODE, "hello", setOf(BarcodeFormat.CODE_128)))
        assertEquals(
            ResolvedBarcodeFormat(BarcodeFormat.QR_CODE, "hello"),
            resolveVisionFormat(BarcodeFormat.QR_CODE, "hello", setOf(BarcodeFormat.QR_CODE)),
        )
    }

    // endregion

    // region Vision

    /** A code to draw: its module matrix (`1` is a dark module), drawn at [scale] points per module. */
    private class TestCode(val rows: List<String>, val scale: Double) {
        val width get() = rows.first().length * scale
    }

    /** A Code 128 barcode from its [bars], [barHeight] modules tall. */
    private fun barcode(bars: String, scale: Double, barHeight: Int = 40) = TestCode(List(barHeight) { bars }, scale)

    /**
     * Draws a [SCENE_WIDTH]x[SCENE_HEIGHT] white scene with the given [code] at ([CODE_X], [CODE_Y]) from its
     * bottom-left corner (Core Graphics coordinates).
     */
    private fun drawScene(context: CGContextRef?, code: TestCode) {
        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, SCENE_WIDTH, SCENE_HEIGHT))
        CGContextSetRGBFillColor(context, 0.0, 0.0, 0.0, 1.0)
        code.rows.forEachIndexed { row, modules ->
            // The first row is the top of the code, and Core Graphics' origin is the bottom-left corner
            val y = CODE_Y + (code.rows.size - 1 - row) * code.scale
            modules.forEachIndexed { column, module ->
                if (module == '1') {
                    CGContextFillRect(context, CGRectMake(CODE_X + column * code.scale, y, code.scale, code.scale))
                }
            }
        }
    }

    private fun renderCGImage(code: TestCode): CGImageRef {
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null,
            SCENE_WIDTH.toULong(),
            SCENE_HEIGHT.toULong(),
            8u,
            0u,
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        try {
            drawScene(context, code)
            return assertNotNull(CGBitmapContextCreateImage(context))
        } finally {
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
    }

    private fun renderPixelBuffer(code: TestCode): CVPixelBufferRef = memScoped {
        val buffer = alloc<CVPixelBufferRefVar>()
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            SCENE_WIDTH.toULong(),
            SCENE_HEIGHT.toULong(),
            kCVPixelFormatType_32BGRA,
            null,
            buffer.ptr,
        )
        val pixelBuffer = assertNotNull(buffer.value)
        CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            CVPixelBufferGetBaseAddress(pixelBuffer),
            SCENE_WIDTH.toULong(),
            SCENE_HEIGHT.toULong(),
            8u,
            CVPixelBufferGetBytesPerRow(pixelBuffer),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little,
        )
        drawScene(context, code)
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
        pixelBuffer
    }

    private fun detector(vararg formats: TargetBarcodeFormat) = VisionBarcodeDetector(formats.toList())

    private fun assertRect(expected: Rect, actual: Rect, message: String) {
        val tolerance = 4.0
        val matches = abs(expected.left - actual.left) < tolerance && abs(expected.right - actual.right) < tolerance &&
            abs(expected.top - actual.top) < tolerance && abs(expected.bottom - actual.bottom) < tolerance
        assertTrue(matches, "$message: expected $expected, got $actual")
    }

    @Test
    fun scansAQRCodeInAnImage() {
        val code = TestCode(TestCodes.QR_URL, scale = 8.0)
        val codeSize = code.width
        val cgImage = renderCGImage(code)
        try {
            val barcode = detector(TargetBarcodeFormat.ALL_FORMATS).detect(UIImage.imageWithCGImage(cgImage)).single()
            assertEquals(BarcodeFormat.QR_CODE, barcode.format)
            assertEquals("https://margelo.com", barcode.rawValue)
            assertEquals(BarcodeValueType.URL, barcode.valueType)
            assertEquals("https://margelo.com", barcode.displayValue)
            assertTrue(barcode.rawBytes?.isNotEmpty() == true)

            // Pixel coordinates with a top-left origin: the code is in [CODE_X, CODE_X + size] horizontally, and
            // [SCENE_HEIGHT - CODE_Y - size, SCENE_HEIGHT - CODE_Y] vertically.
            val box = barcode.boundingBox
            val codeTop = SCENE_HEIGHT - CODE_Y - codeSize
            assertTrue(box.left >= CODE_X && box.right <= CODE_X + codeSize && box.left < box.right, "$box")
            assertTrue(box.top >= codeTop && box.bottom <= SCENE_HEIGHT - CODE_Y && box.top < box.bottom, "$box")

            // ML Kit's corner order: top-left, top-right, bottom-right, bottom-left
            val (topLeft, topRight, bottomRight, bottomLeft) = barcode.cornerPoints
            assertTrue(topLeft.x < topRight.x && topLeft.y < bottomLeft.y, "${barcode.cornerPoints}")
            assertTrue(bottomLeft.x < bottomRight.x && topRight.y < bottomRight.y, "${barcode.cornerPoints}")
            assertEquals(box.left, minOf(topLeft.x, bottomLeft.x), 2.0)
            assertEquals(box.top, minOf(topLeft.y, topRight.y), 2.0)

            // Formats that were not requested are not scanned
            assertTrue(detector(TargetBarcodeFormat.CODE_128).detect(UIImage.imageWithCGImage(cgImage)).isEmpty())
        } finally {
            CGImageRelease(cgImage)
        }
    }

    @Test
    fun reportsImageCoordinatesInTheUprightImage() {
        val cgImage = renderCGImage(TestCode(TestCodes.QR_WIFI, scale = 8.0))
        try {
            val detector = detector(TargetBarcodeFormat.QR_CODE)
            val up = detector.detect(UIImage.imageWithCGImage(cgImage)).single()
            assertEquals(BarcodeValueType.WIFI, up.valueType)
            assertEquals("MyNetwork", up.displayValue)

            // Shown rotated 90° clockwise: a SCENE_HEIGHTxSCENE_WIDTH upright image, where source (x, y) is upright
            // (SCENE_HEIGHT - y, x).
            val rotatedImage = UIImage.imageWithCGImage(cgImage, 1.0, UIImageOrientation.UIImageOrientationRight)
            val rotated = detector.detect(rotatedImage).single()
            assertEquals(up.rawValue, rotated.rawValue)
            val expected = Rect(
                left = SCENE_HEIGHT - up.boundingBox.bottom,
                right = SCENE_HEIGHT - up.boundingBox.top,
                top = up.boundingBox.left,
                bottom = up.boundingBox.right,
            )
            assertRect(expected, rotated.boundingBox, "Rotated image")
        } finally {
            CGImageRelease(cgImage)
        }
    }

    @Test
    fun reportsFrameCoordinatesInThePixelBuffer() {
        val pixelBuffer = renderPixelBuffer(TestCode(TestCodes.QR_PHONE, scale = 8.0))
        try {
            val detector = detector(TargetBarcodeFormat.ALL_FORMATS)
            val up = detector.detect(pixelBuffer, UIImageOrientation.UIImageOrientationUp).single()
            assertEquals(BarcodeValueType.PHONE, up.valueType)
            assertEquals("+15550100", up.displayValue)
            // Whatever the Frame's orientation, the coordinates are in the Frame's (pixel buffer's) coordinate system.
            val right = UIImageOrientation.UIImageOrientationRight
            for (orientation in listOf(right, UIImageOrientation.UIImageOrientationDown)) {
                val barcode = detector.detect(pixelBuffer, orientation).single()
                assertEquals(up.rawValue, barcode.rawValue)
                assertRect(up.boundingBox, barcode.boundingBox, "$orientation")
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }

    @Test
    fun scansACode128() {
        val cgImage = renderCGImage(barcode(TestCodes.CODE_128_BARS, scale = 3.0))
        try {
            val barcode = detector(TargetBarcodeFormat.CODE_128).detect(UIImage.imageWithCGImage(cgImage)).single()
            assertEquals(BarcodeFormat.CODE_128, barcode.format)
            assertEquals("VISION-CAMERA-5", barcode.rawValue)
            assertEquals(BarcodeValueType.TEXT, barcode.valueType)
            assertEquals("VISION-CAMERA-5", barcode.displayValue)
        } finally {
            CGImageRelease(cgImage)
        }
    }

    // endregion

    private companion object {
        const val SCENE_WIDTH = 800.0
        const val SCENE_HEIGHT = 400.0
        const val CODE_X = 80.0
        const val CODE_Y = 60.0
    }
}
