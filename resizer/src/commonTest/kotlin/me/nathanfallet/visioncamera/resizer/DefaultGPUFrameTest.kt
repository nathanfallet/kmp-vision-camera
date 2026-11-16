package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DefaultGPUFrameTest {
    private val options = ResizerOptions(2, 1, ChannelOrder.BGR, DataType.UINT8, ScaleMode.CONTAIN, PixelLayout.PLANAR)
    private val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)

    /** An output view that runs [onCopy] in the middle of each copy, and counts its releases. */
    private class FakeOutputView(private val bytes: ByteArray) : ResizerPipeline.OutputView {
        var onCopy: () -> Unit = {}
        var releaseCount = 0

        override fun copyBytes(): ByteArray {
            onCopy()
            check(releaseCount == 0) { "The output buffer was released before the end of a copy" }
            return bytes.copyOf()
        }

        override fun release() {
            releaseCount++
        }
    }

    private val outputView = FakeOutputView(bytes)
    private var onReleaseCount = 0
    private val frame = DefaultGPUFrame(options, outputView) { onReleaseCount++ }

    private fun assertReleasedOnce() {
        assertEquals(1, outputView.releaseCount, "The output buffer must be released exactly once")
        assertEquals(1, onReleaseCount, "onRelease must be called exactly once")
    }

    @Test
    fun disposeReleasesTheOutputBufferExactlyOnce() {
        assertContentEquals(bytes, frame.getPixelBuffer())
        assertEquals(0, outputView.releaseCount)
        assertSame(outputView, frame.requireOutputView())
        frame.dispose()
        assertReleasedOnce()
        frame.dispose() // no-op
        assertReleasedOnce()
    }

    @Test
    fun disposedGpuFrameThrowsOnAccess() {
        frame.dispose()
        assertFailsWith<VisionCameraException> { frame.getPixelBuffer() }
        assertFailsWith<VisionCameraException> { frame.requireOutputView() }
        assertEquals(0, frame.width)
        assertReleasedOnce()
    }

    @Test
    fun disposeDuringACopyReleasesTheOutputBufferAfterTheCopy() {
        // dispose() lands (e.g. from another thread) while getPixelBuffer() is copying the output buffer.
        outputView.onCopy = { frame.dispose() }
        assertContentEquals(bytes, frame.getPixelBuffer())
        assertReleasedOnce()
        outputView.onCopy = {}
        assertFailsWith<VisionCameraException> { frame.getPixelBuffer() }
        frame.dispose() // no-op
        assertReleasedOnce()
    }

    @Test
    fun disposeDuringConcurrentCopiesReleasesTheOutputBufferAfterTheLastOne() {
        // Two copies are in progress (the second one starts during the first one) when dispose() lands.
        var copies = 0
        outputView.onCopy = {
            copies++
            if (copies == 1) {
                assertContentEquals(bytes, frame.getPixelBuffer())
                assertEquals(0, outputView.releaseCount, "Released while the first copy is still in progress")
            } else {
                frame.dispose()
            }
        }
        assertContentEquals(bytes, frame.getPixelBuffer())
        assertEquals(2, copies)
        assertReleasedOnce()
    }
}
