package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import java.nio.ByteBuffer

/**
 * Gets a direct [ByteBuffer] over this [GPUFrame]'s mapped Vulkan output memory, without copying (the equivalent of
 * VisionCamera's zero-copy `getPixelBuffer()` `ArrayBuffer`), in the native byte order. It can be passed as is to ML
 * runtimes such as LiteRT (TensorFlow Lite) or ONNX Runtime.
 *
 * The returned [ByteBuffer] is only valid as long as this [GPUFrame] is valid: once the [GPUFrame] has been
 * [GPUFrame.dispose]d, it is no longer safe to access and may return "garbage data", or crash if the [Resizer] was
 * disposed too. Use [GPUFrame.getPixelBuffer] for a copy that outlives the [GPUFrame].
 *
 * ```kotlin
 * resizer.resize(frame).use { resized ->
 *     interpreter.run(resized.getPixelByteBuffer(), output)
 * }
 * ```
 *
 * @throws VisionCameraException If this [GPUFrame] has already been disposed.
 */
fun GPUFrame.getPixelByteBuffer(): ByteBuffer {
    val frame = this as? DefaultGPUFrame ?: throw VisionCameraException("This GPUFrame was not created by a Resizer!")
    val outputView = frame.requireOutputView() as VulkanResizerPipeline.OutputView
    return outputView.getByteBuffer()
}
