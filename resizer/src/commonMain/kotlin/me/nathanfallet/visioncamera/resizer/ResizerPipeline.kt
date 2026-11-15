package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.VisionCameraException

/**
 * The GPU pipeline of a [Resizer] on the current platform (V5's `VulkanResizerPipeline` on Android,
 * `MetalResizerPipeline` on iOS), for one fixed [ResizerOptions]. It owns one reusable output buffer.
 *
 * Its functions are called by [DefaultResizer], which guarantees that [run] is never called while an [OutputView] is
 * alive or concurrently with [destroy], and that [destroy] is called once, after the last [OutputView] was released.
 */
internal interface ResizerPipeline {
    /** The options this pipeline was created with. */
    val options: ResizerOptions

    /**
     * Resizes the given native buffer (a `CVPixelBufferRef` on iOS, an `AHardwareBuffer*` on Android, borrowed for
     * the duration of the call) into the output buffer, and returns a view over it.
     *
     * @throws VisionCameraException If the GPU pipeline failed.
     */
    fun run(nativeBufferPointer: Long, rotationDegrees: Int, isMirrored: Boolean): OutputView

    /** Releases the GPU resources of this pipeline. */
    fun destroy()

    /**
     * A view over the pipeline's output buffer, valid until it is [release]d.
     */
    interface OutputView {
        /** Copies the output buffer into a new [ByteArray]. */
        fun copyBytes(): ByteArray

        /** Releases this view, so the pipeline can run again. */
        fun release()
    }
}
