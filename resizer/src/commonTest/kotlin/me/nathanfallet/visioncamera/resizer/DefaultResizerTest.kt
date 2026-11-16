package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultResizerTest {
    private val options = ResizerOptions(2, 1, ChannelOrder.BGR, DataType.UINT8, ScaleMode.CONTAIN, PixelLayout.PLANAR)

    @Test
    fun resizeRunsThePipelineAndReleasesTheNativeBuffer() {
        val pipeline = FakePipeline(options)
        val resizer = DefaultResizer(pipeline)
        val nativeBuffer = FakeNativeBuffer()
        val frame = resizer.resize({ nativeBuffer }, 90, true)
        assertEquals(listOf(Triple(42L, 90, true)), pipeline.runs)
        assertEquals(1, nativeBuffer.releaseCount)
        assertEquals(2, frame.width)
        assertEquals(1, frame.height)
        assertEquals(ChannelOrder.BGR, frame.channelOrder)
        assertEquals(DataType.UINT8, frame.dataType)
        assertEquals(PixelLayout.PLANAR, frame.pixelLayout)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), frame.getPixelBuffer())
    }

    @Test
    fun onlyOneGpuFrameCanBeAliveAtATime() {
        val pipeline = FakePipeline(options)
        val resizer = DefaultResizer(pipeline)
        val frame = resizer.resize(::FakeNativeBuffer, 0, false)
        assertFailsWith<VisionCameraException> { resizer.resize(::FakeNativeBuffer, 0, false) }
        frame.dispose()
        frame.dispose() // no-op
        resizer.resize(::FakeNativeBuffer, 0, false).use { }
        assertEquals(2, pipeline.runs.size)
    }

    @Test
    fun disposedGpuFrameHasNoPixels() {
        val frame = DefaultResizer(FakePipeline(options)).resize(::FakeNativeBuffer, 0, false)
        frame.dispose()
        assertEquals(0, frame.width)
        assertEquals(0, frame.height)
        assertNull(frame.channelOrder)
        assertNull(frame.dataType)
        assertNull(frame.pixelLayout)
        assertFailsWith<VisionCameraException> { frame.getPixelBuffer() }
    }

    @Test
    fun disposeDestroysThePipelineOnceTheGpuFrameIsDisposed() {
        val pipeline = FakePipeline(options)
        val resizer = DefaultResizer(pipeline)
        val frame = resizer.resize(::FakeNativeBuffer, 0, false)
        resizer.dispose()
        assertFalse(pipeline.isDestroyed)
        assertFailsWith<VisionCameraException> { resizer.resize(::FakeNativeBuffer, 0, false) }
        frame.dispose()
        assertTrue(pipeline.isDestroyed)
        resizer.dispose() // no-op
        assertFailsWith<VisionCameraException> { resizer.resize(::FakeNativeBuffer, 0, false) }
    }

    @Test
    fun disposeDestroysAnIdlePipelineRightAway() {
        val pipeline = FakePipeline(options)
        DefaultResizer(pipeline).use { }
        assertTrue(pipeline.isDestroyed)
    }

    @Test
    fun failedResizeFreesTheOutputBuffer() {
        val pipeline = FakePipeline(options)
        val resizer = DefaultResizer(pipeline)
        val nativeBuffer = FakeNativeBuffer()
        pipeline.failure = VisionCameraException("GPU failure")
        assertFailsWith<VisionCameraException> { resizer.resize({ nativeBuffer }, 0, false) }
        assertEquals(1, nativeBuffer.releaseCount)
        pipeline.failure = null
        resizer.resize(::FakeNativeBuffer, 0, false).dispose()
        assertFailsWith<VisionCameraException> {
            resizer.resize({ throw VisionCameraException("No native buffer") }, 0, false)
        }
        resizer.dispose()
        assertTrue(pipeline.isDestroyed)
    }
}
