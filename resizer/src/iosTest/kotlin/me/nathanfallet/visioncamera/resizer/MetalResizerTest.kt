package me.nathanfallet.visioncamera.resizer

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toLong
import kotlinx.cinterop.value
import kotlinx.coroutines.test.runTest
import me.nathanfallet.visioncamera.NativeBuffer
import me.nathanfallet.visioncamera.VisionCameraException
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferIOSurfacePropertiesKey
import platform.CoreVideo.kCVPixelBufferMetalCompatibilityKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Resizes synthetic camera frames with the real Metal pipeline: a 64x64 `420f` pixel buffer whose left half is dark
 * gray (Y = 51) and right half light gray (Y = 204).
 */
class MetalResizerTest {
    private fun options(
        scaleMode: ScaleMode,
        dataType: DataType = DataType.UINT8,
        layout: PixelLayout = PixelLayout.INTERLEAVED,
    ) = ResizerOptions(8, 4, ChannelOrder.RGB, dataType, scaleMode, layout)

    /** Skips the test (returning `false`) where Metal is not available, e.g. a Simulator without GPU. */
    private fun isMetalAvailable(): Boolean =
        isResizerAvailable().also { if (!it) println("Metal is not available, skipping the resizer test.") }

    private fun createPixelBuffer(
        pixelFormat: UInt = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
    ): CVPixelBufferRef =
        memScoped {
            // IOSurface-backed, like camera buffers, so that Metal can wrap its planes into textures.
            val surfaceProperties =
                CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            val attributes =
                CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionarySetValue(attributes, kCVPixelBufferIOSurfacePropertiesKey, surfaceProperties)
            CFDictionarySetValue(attributes, kCVPixelBufferMetalCompatibilityKey, kCFBooleanTrue)
            val pixelBuffer = alloc<CVPixelBufferRefVar>()
            val status = CVPixelBufferCreate(null, 64u, 64u, pixelFormat, attributes, pixelBuffer.ptr)
            CFRelease(attributes)
            CFRelease(surfaceProperties)
            assertEquals(0, status)
            val buffer = pixelBuffer.value!!
            if (pixelFormat == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange) {
                CVPixelBufferLockBaseAddress(buffer, 0u)
                val y = CVPixelBufferGetBaseAddressOfPlane(buffer, 0u)!!.reinterpret<UByteVar>()
                val yBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(buffer, 0u).toInt()
                for (row in 0 until 64) for (column in 0 until 64) {
                    y[row * yBytesPerRow + column] = if (column < 32) 51u else 204u
                }
                val uv = CVPixelBufferGetBaseAddressOfPlane(buffer, 1u)!!.reinterpret<UByteVar>()
                val uvBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(buffer, 1u).toInt()
                for (row in 0 until 32) for (column in 0 until 64) {
                    uv[row * uvBytesPerRow + column] = 128u
                }
                CVPixelBufferUnlockBaseAddress(buffer, 0u)
            }
            buffer
        }

    private fun CVPixelBufferRef.asNativeBuffer(): NativeBuffer = object : NativeBuffer {
        override val pointer: Long = this@asNativeBuffer.toLong()

        override fun release() = Unit
    }

    /** Resizes the synthetic frame, and returns the R values of the output's second row. */
    private suspend fun resizeRow(options: ResizerOptions, isMirrored: Boolean = false): List<Int> {
        val pixelBuffer = createPixelBuffer()
        try {
            createResizer(options).use { resizer ->
                (resizer as DefaultResizer).resize({ pixelBuffer.asNativeBuffer() }, 0, isMirrored).use { frame ->
                    val pixels = frame.getPixelBuffer()
                    assertEquals(8 * 4 * 3, pixels.size)
                    assertEquals(pixels.size.toULong(), frame.getPixelData().length)
                    return (0 until 8).map { pixels[(8 + it) * 3].toInt() and 0xFF }
                }
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }

    @Test
    fun coverFillsTheOutput() = runTest {
        if (!isMetalAvailable()) return@runTest
        assertEquals(listOf(52, 52, 52, 52, 205, 205, 205, 205), resizeRow(options(ScaleMode.COVER)))
    }

    @Test
    fun containAddsBlackBars() = runTest {
        if (!isMetalAvailable()) return@runTest
        assertEquals(listOf(0, 0, 52, 52, 205, 205, 0, 0), resizeRow(options(ScaleMode.CONTAIN)))
    }

    @Test
    fun stretchAndMirror() = runTest {
        if (!isMetalAvailable()) return@runTest
        assertEquals(
            listOf(205, 205, 205, 205, 52, 52, 52, 52),
            resizeRow(options(ScaleMode.STRETCH), isMirrored = true),
        )
    }

    @Test
    fun float32PlanarWritesNormalizedChannelPlanes() = runTest {
        if (!isMetalAvailable()) return@runTest
        val pixelBuffer = createPixelBuffer()
        try {
            createResizer(options(ScaleMode.STRETCH, DataType.FLOAT32, PixelLayout.PLANAR)).use { resizer ->
                (resizer as DefaultResizer).resize({ pixelBuffer.asNativeBuffer() }, 0, false).use { frame ->
                    val bytes = frame.getPixelBuffer()
                    assertEquals(8 * 4 * 3 * 4, bytes.size)
                    val values = (0 until bytes.size / 4).map { index ->
                        val bits = (0 until 4).fold(0) { value, byte ->
                            value or ((bytes[index * 4 + byte].toInt() and 0xFF) shl (byte * 8))
                        }
                        Float.fromBits(bits)
                    }
                    // Each channel plane (R, then G, then B) has the dark left half and the light right half.
                    for (channel in 0 until 3) {
                        val row = values.subList(channel * 32 + 8, channel * 32 + 16)
                        assertTrue(row.take(4).all { abs(it - 0.2f) < 0.01f }, "$row")
                        assertTrue(row.drop(4).all { abs(it - 0.8f) < 0.01f }, "$row")
                    }
                }
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }

    @Test
    fun unsupportedPixelFormatThrows() = runTest {
        if (!isMetalAvailable()) return@runTest
        val pixelBuffer = createPixelBuffer(kCVPixelFormatType_32BGRA)
        try {
            createResizer(options(ScaleMode.COVER)).use { resizer ->
                assertFailsWith<VisionCameraException> {
                    (resizer as DefaultResizer).resize({ pixelBuffer.asNativeBuffer() }, 0, false)
                }
                // The output buffer is free again.
                val goodBuffer = createPixelBuffer()
                (resizer as DefaultResizer).resize({ goodBuffer.asNativeBuffer() }, 0, false).dispose()
                CVPixelBufferRelease(goodBuffer)
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }
}
