package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException
import platform.Foundation.NSData

/**
 * Gets an [NSData] over this [GPUFrame]'s shared Metal output buffer, without copying (the equivalent of
 * VisionCamera's zero-copy `getPixelBuffer()` `ArrayBuffer`). Its `bytes` can be passed as is to ML runtimes, e.g. to
 * a Core ML `MLMultiArray(dataPointer:...)`.
 *
 * The returned [NSData] is only valid as long as this [GPUFrame] is valid: once the [GPUFrame] has been
 * [GPUFrame.dispose]d, it is no longer safe to access and may return "garbage data", or crash if the [Resizer] was
 * disposed too. Use [GPUFrame.getPixelBuffer] for a copy that outlives the [GPUFrame].
 *
 * ```kotlin
 * resizer.resize(frame).use { resized ->
 *     val data = resized.getPixelData()
 *     // run your ML model with data.bytes...
 * }
 * ```
 *
 * @throws VisionCameraException If this [GPUFrame] has already been disposed.
 */
fun GPUFrame.getPixelData(): NSData {
    val frame = this as? DefaultGPUFrame ?: throw VisionCameraException("This GPUFrame was not created by a Resizer!")
    val outputView = frame.requireOutputView() as MetalResizerPipeline.OutputView
    return outputView.getData()
}
