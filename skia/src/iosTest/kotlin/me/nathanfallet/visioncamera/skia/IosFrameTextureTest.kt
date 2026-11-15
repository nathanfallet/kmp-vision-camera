package me.nathanfallet.visioncamera.skia

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import me.nathanfallet.visioncamera.NativeBuffer
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferIOSurfacePropertiesKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.CoreVideo.kCVReturnSuccess
import platform.Metal.MTLCreateSystemDefaultDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wraps real IOSurface-backed CVPixelBuffers as Skia GPU images (the zero-copy path of [renderToTexture]), draws them
 * and reads them back. Skipped where Metal is not available.
 */
class IosFrameTextureTest {
    private val width = 64
    private val height = 32

    @Test
    fun bgraPixelBuffersAreDrawable() = withMetal {
        val pixelBuffer = createPixelBuffer(kCVPixelFormatType_32BGRA) {
            val base = CVPixelBufferGetBaseAddress(it)!!.reinterpret<UByteVar>()
            val bytesPerRow = CVPixelBufferGetBytesPerRow(it).toInt()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = y * bytesPerRow + x * 4
                    base[offset] = 0u // B
                    base[offset + 1] = 0u // G
                    base[offset + 2] = 255u // R
                    base[offset + 3] = 255u // A
                }
            }
        }
        assertRed(drawPixelBuffer(pixelBuffer))
    }

    @Test
    fun yuvPixelBuffersAreDrawable() = withMetal {
        val pixelBuffer = createPixelBuffer(kCVPixelFormatType_420YpCbCr8BiPlanarFullRange) {
            // Pure red in full-range BT.709: Y = 54, Cb = 99, Cr = 255
            val yPlane = CVPixelBufferGetBaseAddressOfPlane(it, 0u)!!.reinterpret<UByteVar>()
            val yBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(it, 0u).toInt()
            for (y in 0 until height) for (x in 0 until width) yPlane[y * yBytesPerRow + x] = 54u
            val uvPlane = CVPixelBufferGetBaseAddressOfPlane(it, 1u)!!.reinterpret<UByteVar>()
            val uvBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(it, 1u).toInt()
            for (y in 0 until height / 2) {
                for (x in 0 until width / 2) {
                    uvPlane[y * uvBytesPerRow + x * 2] = 99u
                    uvPlane[y * uvBytesPerRow + x * 2 + 1] = 255u
                }
            }
        }
        assertRed(drawPixelBuffer(pixelBuffer))
    }

    private fun withMetal(block: () -> Unit) {
        if (MTLCreateSystemDefaultDevice() == null) {
            println("Metal is not available, skipping")
            return
        }
        try {
            block()
        } finally {
            clearSurfacesCache()
        }
    }

    private fun createPixelBuffer(format: UInt, fill: (CVPixelBufferRef) -> Unit): CVPixelBufferRef = memScoped {
        val attributes =
            CFDictionaryCreateMutable(null, 1, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val ioSurfaceProperties =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(attributes, kCVPixelBufferIOSurfacePropertiesKey, ioSurfaceProperties)
        val pixelBuffer = alloc<CVPixelBufferRefVar>()
        val result = CVPixelBufferCreate(null, width.toULong(), height.toULong(), format, attributes, pixelBuffer.ptr)
        CFRelease(ioSurfaceProperties)
        CFRelease(attributes)
        assertEquals(kCVReturnSuccess, result)
        val buffer = pixelBuffer.value!!
        CVPixelBufferLockBaseAddress(buffer, 0u)
        fill(buffer)
        CVPixelBufferUnlockBaseAddress(buffer, 0u)
        buffer
    }

    /** Draws the pixel buffer with [IosSkiaGpuContext] in a cached surface, and copies the result to the CPU. */
    private fun drawPixelBuffer(pixelBuffer: CVPixelBufferRef): Image {
        var isReleased = false
        val nativeBuffer = object : NativeBuffer {
            override val pointer: Long = pixelBuffer.rawValue.toLong()

            override fun release() {
                CVPixelBufferRelease(pixelBuffer)
                isReleased = true
            }
        }
        val image = surfacesCache.withSurface(width, height) { context, surface ->
            val frameTexture = context.makeFrameTexture(nativeBuffer)
            assertEquals(width, frameTexture.image.width)
            assertEquals(height, frameTexture.image.height)
            surface.canvas.drawImage(frameTexture.image, 0f, 0f)
            val snapshot = surface.makeImageSnapshot()
            frameTexture.release()
            nativeBuffer.release()
            try {
                snapshot.makeNonTextureImage()
            } finally {
                snapshot.close()
            }
        }
        assertTrue(isReleased)
        return image
    }

    private fun assertRed(image: Image) {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        val color = bitmap.getColor(width / 2, height / 2)
        bitmap.close()
        image.close()
        assertTrue(Color.getR(color) > 230, "Red channel of ${color.toUInt().toString(16)}")
        assertTrue(Color.getG(color) < 30, "Green channel of ${color.toUInt().toString(16)}")
        assertTrue(Color.getB(color) < 30, "Blue channel of ${color.toUInt().toString(16)}")
    }
}
