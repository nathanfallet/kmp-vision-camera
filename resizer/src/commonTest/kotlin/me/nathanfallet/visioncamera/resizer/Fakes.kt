package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.NativeBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A [NativeBuffer] that counts its releases. */
internal class FakeNativeBuffer : NativeBuffer {
    override val pointer: Long = 42
    var releaseCount = 0

    override fun release() {
        releaseCount++
    }
}

/** A [ResizerPipeline] that records its runs, and checks that it is never used while an output view is alive. */
internal class FakePipeline(override val options: ResizerOptions) : ResizerPipeline {
    var runs = mutableListOf<Triple<Long, Int, Boolean>>()
    var liveViews = 0
    var isDestroyed = false
    var failure: Exception? = null

    override fun run(nativeBufferPointer: Long, rotationDegrees: Int, isMirrored: Boolean): ResizerPipeline.OutputView {
        assertFalse(isDestroyed)
        assertEquals(0, liveViews, "The pipeline ran while an output view was alive")
        failure?.let { throw it }
        runs += Triple(nativeBufferPointer, rotationDegrees, isMirrored)
        liveViews++
        return object : ResizerPipeline.OutputView {
            override fun copyBytes() = byteArrayOf(1, 2, 3, 4, 5, 6)

            override fun release() {
                liveViews--
            }
        }
    }

    override fun destroy() {
        assertEquals(0, liveViews, "The pipeline was destroyed while an output view was alive")
        assertFalse(isDestroyed, "The pipeline was destroyed twice")
        isDestroyed = true
    }
}
