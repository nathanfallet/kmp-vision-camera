package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The [GPUFrame] of every platform (V5's `HybridGPUFrame`): a view over its [Resizer]'s output buffer, until it is
 * disposed.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class DefaultGPUFrame(
    private val options: ResizerOptions,
    outputView: ResizerPipeline.OutputView,
    private val onRelease: () -> Unit,
) : GPUFrame {
    private val outputView = AtomicReference<ResizerPipeline.OutputView?>(outputView)

    private val isValid: Boolean get() = outputView.load() != null

    override val width: Int get() = if (isValid) options.width else 0
    override val height: Int get() = if (isValid) options.height else 0
    override val channelOrder: ChannelOrder? get() = options.channelOrder.takeIf { isValid }
    override val dataType: DataType? get() = options.dataType.takeIf { isValid }
    override val pixelLayout: PixelLayout? get() = options.pixelLayout.takeIf { isValid }

    override fun getPixelBuffer(): ByteArray = requireOutputView().copyBytes()

    /**
     * The view over the output buffer, for the platform's zero-copy accessors.
     *
     * @throws VisionCameraException If this [GPUFrame] has already been disposed.
     */
    fun requireOutputView(): ResizerPipeline.OutputView =
        outputView.load() ?: throw VisionCameraException("This GPUFrame has already been disposed!")

    override fun dispose() {
        val outputView = outputView.exchange(null) ?: return
        outputView.release()
        onRelease()
    }

    override fun toString(): String = "GPUFrame(${width}x$height, $channelOrder, $dataType, $pixelLayout)"
}
