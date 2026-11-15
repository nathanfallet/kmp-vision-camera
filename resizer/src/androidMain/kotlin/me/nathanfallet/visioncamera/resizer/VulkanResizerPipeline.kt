package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Android [ResizerPipeline]: V5's Vulkan C++ `VulkanResizerPipeline`, in `libVisionCameraResizer.so`.
 */
internal class VulkanResizerPipeline(
    override val options: ResizerOptions,
) : ResizerPipeline {
    private val handle: Long = native {
        ResizerNative.createPipeline(
            options.width,
            options.height,
            options.channelOrder.shaderOrdinal,
            options.dataType.shaderOrdinal,
            options.scaleMode.shaderOrdinal,
            options.pixelLayout.shaderOrdinal,
        )
    }

    override fun run(nativeBufferPointer: Long, rotationDegrees: Int, isMirrored: Boolean): ResizerPipeline.OutputView =
        OutputView(native { ResizerNative.run(handle, nativeBufferPointer, rotationDegrees, isMirrored) })

    override fun destroy() {
        native { ResizerNative.destroyPipeline(handle) }
    }

    /** A view over the mapped Vulkan output buffer (V5's `VulkanBufferView`). */
    class OutputView(private val frameHandle: Long) : ResizerPipeline.OutputView {
        override fun copyBytes(): ByteArray = native { ResizerNative.copyFrameBytes(frameHandle) }

        /** Wraps the mapped output buffer in a direct [ByteBuffer], in the native byte order, without copying. */
        fun getByteBuffer(): ByteBuffer =
            native { ResizerNative.getFrameByteBuffer(frameHandle) }.order(ByteOrder.nativeOrder())

        override fun release() {
            ResizerNative.releaseFrame(frameHandle)
        }
    }

    private companion object {
        /** Runs the given native call, rethrowing its errors as [VisionCameraException]s. */
        inline fun <T> native(call: () -> T): T =
            try {
                call()
            } catch (error: IllegalStateException) {
                throw VisionCameraException(error.message ?: "The Vulkan resizer pipeline failed.", error)
            }
    }
}
