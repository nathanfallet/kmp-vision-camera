package me.nathanfallet.visioncamera.resizer

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class ResizerFactoryTest {
    private val options = ResizerOptions(2, 1, ChannelOrder.BGR, DataType.UINT8, ScaleMode.CONTAIN, PixelLayout.PLANAR)

    @Test
    fun createResizerReturnsAResizerOverThePipeline() = runTest {
        val pipeline = FakePipeline(options)
        val resizer = createResizer(options) { requestedOptions ->
            assertSame(options, requestedOptions)
            pipeline
        }
        assertIs<DefaultResizer>(resizer).resize(::FakeNativeBuffer, 0, false).dispose()
        assertEquals(1, pipeline.runs.size)
        assertFalse(pipeline.isDestroyed, "The returned Resizer owns the pipeline")
        resizer.dispose()
        assertTrue(pipeline.isDestroyed)
    }

    @Test
    fun createResizerFreesThePipelineIfTheCallerIsCancelledMeanwhile() = runTest {
        val pipeline = FakePipeline(options)
        lateinit var caller: Job
        caller = launch {
            createResizer(options) {
                // The caller is cancelled while the pipeline is being created: nobody will get the Resizer.
                caller.cancel()
                pipeline
            }
            fail("createResizer returned a Resizer to a cancelled caller")
        }
        caller.join()
        assertTrue(caller.isCancelled)
        assertTrue(pipeline.isDestroyed, "The pipeline of a Resizer nobody got must be destroyed")
    }

    @Test
    fun createResizerThrowsIfThePipelineCantBeCreated() = runTest {
        assertFailsWith<VisionCameraException> {
            createResizer(options) { throw VisionCameraException("No GPU") }
        }
    }
}
