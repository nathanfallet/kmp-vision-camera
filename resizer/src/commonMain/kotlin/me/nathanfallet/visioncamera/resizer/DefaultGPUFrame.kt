package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The [GPUFrame] of every platform (V5's `HybridGPUFrame`): a view over its [Resizer]'s output buffer, until it is
 * disposed.
 *
 * Copies of the output buffer ([getPixelBuffer]) may run on another thread than [dispose] (V5 guards them with a
 * mutex): the output buffer is only released once the copies in progress are done.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class DefaultGPUFrame(
    private val options: ResizerOptions,
    outputView: ResizerPipeline.OutputView,
    private val onRelease: () -> Unit,
) : GPUFrame {
    private val outputView: ResizerPipeline.OutputView = outputView

    /** The number of copies in progress, plus [DISPOSED] once [dispose] was called. */
    private val state = AtomicInt(0)

    private val isValid: Boolean get() = state.load() and DISPOSED == 0

    override val width: Int get() = if (isValid) options.width else 0
    override val height: Int get() = if (isValid) options.height else 0
    override val channelOrder: ChannelOrder? get() = options.channelOrder.takeIf { isValid }
    override val dataType: DataType? get() = options.dataType.takeIf { isValid }
    override val pixelLayout: PixelLayout? get() = options.pixelLayout.takeIf { isValid }

    override fun getPixelBuffer(): ByteArray {
        while (true) {
            val current = state.load()
            if (current and DISPOSED != 0) throw disposedException()
            if (state.compareAndSet(current, current + 1)) break
        }
        try {
            return outputView.copyBytes()
        } finally {
            // The last copy to end after dispose() releases the output buffer.
            if (state.addAndFetch(-1) == DISPOSED) release()
        }
    }

    /**
     * The view over the output buffer, for the platform's zero-copy accessors (only valid until [dispose]).
     *
     * @throws VisionCameraException If this [GPUFrame] has already been disposed.
     */
    fun requireOutputView(): ResizerPipeline.OutputView =
        if (isValid) outputView else throw disposedException()

    override fun dispose() {
        while (true) {
            val current = state.load()
            if (current and DISPOSED != 0) return
            if (state.compareAndSet(current, current or DISPOSED)) {
                // Without copies in progress, release now; otherwise the last one releases.
                if (current == 0) release()
                return
            }
        }
    }

    private fun release() {
        outputView.release()
        onRelease()
    }

    private fun disposedException() = VisionCameraException("This GPUFrame has already been disposed!")

    override fun toString(): String = "GPUFrame(${width}x$height, $channelOrder, $dataType, $pixelLayout)"

    private companion object {
        /** The flag of [state] set by [dispose]. */
        const val DISPOSED = 1 shl 30
    }
}
