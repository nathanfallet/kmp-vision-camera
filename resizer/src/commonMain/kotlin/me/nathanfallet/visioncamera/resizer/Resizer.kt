package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.CameraFrameOutput
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.VisionCameraException

/**
 * Represents a GPU-accelerated [Frame] resizer and converter.
 *
 * You can create instances of the [Resizer] via [createResizer].
 *
 * The [Resizer] internally allocates a memory pool, which can grow quite large, depending on texture size. When you
 * are done with the [Resizer], it is recommended to [dispose] it (or use it in a `use { }` block) to free up
 * resources.
 */
interface Resizer : AutoCloseable {
    /**
     * Resize the given [frame] using the options this [Resizer] was configured with.
     *
     * - On iOS, the input [Frame]'s [Frame.pixelFormat] has to be `yuv-420-8-bit-full`
     *   ([me.nathanfallet.visioncamera.VideoPixelFormat.YUV_420_8_BIT_FULL]), which is configured on the
     *   [CameraFrameOutput] by setting its pixel format to [me.nathanfallet.visioncamera.TargetVideoPixelFormat.YUV].
     * - On Android, the input [Frame]'s [Frame.pixelFormat] can be any pixel format, even
     *   [me.nathanfallet.visioncamera.PixelFormat.Private]: it is imported in Vulkan as an `AHardwareBuffer`, which
     *   requires Android 9 (API 28).
     *
     * For maximum performance, the resolved session's native pixel format should be `yuv-420-8-bit-full` on iOS, and
     * `private` on Android: then, set the frame output's pixel format to
     * [me.nathanfallet.visioncamera.TargetVideoPixelFormat.NATIVE] on Android, and to
     * [me.nathanfallet.visioncamera.TargetVideoPixelFormat.NATIVE] on iOS if the native pixel format really is
     * `yuv-420-8-bit-full` (or [me.nathanfallet.visioncamera.TargetVideoPixelFormat.YUV] otherwise). To configure your
     * session with the target pixel format, use a pixel format constraint.
     *
     * For ML models that expect CHW / NCHW tensors, configure the [Resizer] with [PixelLayout.PLANAR].
     *
     * The returned [GPUFrame] must be disposed (via [GPUFrame.dispose], or a `use { }` block) to ensure the pipeline
     * can run continuously: the [Resizer] has one output buffer, so calling [resize] again while the previous
     * [GPUFrame] is alive throws.
     *
     * The [frame] is only read during this call: it can be disposed as soon as this returns.
     *
     * ```kotlin
     * val resizer = createResizer(options)
     * frameOutput.setOnFrameCallback { frame ->
     *     frame.use {
     *         resizer.resize(it).use { resized ->
     *             val pixels = resized.getPixelBuffer()
     *             // run your ML model...
     *         }
     *     }
     * }
     * ```
     *
     * @return A [GPUFrame] of the converted and resized pixel data.
     * @throws VisionCameraException If this [Resizer] has been disposed, the previous [GPUFrame] is still alive, the
     * [frame] has no native buffer or an unsupported pixel format, or the GPU pipeline failed.
     */
    fun resize(frame: Frame): GPUFrame

    /**
     * Disposes this [Resizer], releasing its GPU resources. It must not be used afterwards. If a [GPUFrame] of this
     * [Resizer] is still alive, the resources are released once that [GPUFrame] is disposed. Calling it more than once
     * is a no-op.
     */
    fun dispose()

    /**
     * Same as [dispose], to use a [Resizer] with `use { }`.
     */
    override fun close() {
        dispose()
    }
}
