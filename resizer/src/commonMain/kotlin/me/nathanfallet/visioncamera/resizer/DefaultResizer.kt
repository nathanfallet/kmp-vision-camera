package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.NativeBuffer
import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The [Resizer] of every platform (V5's `HybridResizer`), running a platform [ResizerPipeline].
 *
 * Its pipeline has one output buffer, so it tracks whether a [GPUFrame] is alive (V5's `MetalReusableBuffer` /
 * `VulkanReusableBuffer` "in use" flag). Unlike V5's Android implementation, which throws when a [Resizer] is disposed
 * while its [GPUFrame] is alive, the pipeline is then destroyed once that [GPUFrame] is disposed (like V5 on iOS, where
 * the [GPUFrame] keeps the output buffer alive): a Compose `rememberResizer` can dispose it from the UI thread while a
 * frame is being processed.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class DefaultResizer(
    private val pipeline: ResizerPipeline,
) : Resizer {
    /** [IDLE], [BUSY] (resizing, or its [GPUFrame] is alive) or [DESTROYED]. */
    private val state = AtomicInt(IDLE)
    private val isDisposeRequested = AtomicBoolean(false)

    override fun resize(frame: Frame): GPUFrame =
        resize(frame::getNativeBuffer, frame.orientation.shaderRotationDegrees, frame.isMirrored)

    /**
     * Resizes the [NativeBuffer] that `getNativeBuffer` gets from a Frame (it is released once the GPU is done with
     * it), with the Frame's rotation (in degrees) and mirroring.
     */
    fun resize(getNativeBuffer: () -> NativeBuffer, rotationDegrees: Int, isMirrored: Boolean): GPUFrame {
        if (isDisposeRequested.load()) {
            throw VisionCameraException(DISPOSED_MESSAGE)
        }
        if (!state.compareAndSet(IDLE, BUSY)) {
            throw VisionCameraException(
                if (state.load() == DESTROYED) {
                    DISPOSED_MESSAGE
                } else {
                    "Previous GPUFrame is still in-use. Dispose it before calling resize() again."
                },
            )
        }
        var isRunning = true
        try {
            // Keep the NativeBuffer alive while the GPU borrows the underlying platform buffer.
            val nativeBuffer = getNativeBuffer()
            val outputView = try {
                pipeline.run(nativeBuffer.pointer, rotationDegrees, isMirrored)
            } finally {
                nativeBuffer.release()
            }
            isRunning = false
            return DefaultGPUFrame(pipeline.options, outputView, ::releaseOutput)
        } finally {
            if (isRunning) {
                // The pipeline failed: free the output buffer again.
                releaseOutput()
            }
        }
    }

    override fun dispose() {
        isDisposeRequested.store(true)
        destroyIfRequested()
    }

    /** Called once the output buffer is free again: the [GPUFrame] was disposed, or [resize] failed. */
    private fun releaseOutput() {
        state.store(IDLE)
        destroyIfRequested()
    }

    private fun destroyIfRequested() {
        if (isDisposeRequested.load() && state.compareAndSet(IDLE, DESTROYED)) {
            pipeline.destroy()
        }
    }

    private companion object {
        const val IDLE = 0
        const val BUSY = 1
        const val DESTROYED = 2
        const val DISPOSED_MESSAGE = "This Resizer has already been disposed!"
    }
}
