package me.nathanfallet.visioncamera

/**
 * Configuration options for a [CameraFrameOutput].
 *
 * @see CameraFactory.createFrameOutput
 */
data class FrameOutputOptions(
    /**
     * The target Frame resolution. The [CameraSession] negotiates it with the other outputs and constraints, so it may
     * not be met exactly; the aspect ratio is then prioritized over the pixel count.
     */
    val targetResolution: Size = CommonResolutions.HD_16_9,
    /**
     * The [TargetVideoPixelFormat] of the Frames.
     *
     * - [TargetVideoPixelFormat.NATIVE] is the most efficient (zero-copy, GPU only), and recommended for GPU pipelines.
     * - [TargetVideoPixelFormat.YUV] is the next best choice to access the pixels on the CPU.
     * - [TargetVideoPixelFormat.RGB] uses ~2.6x more bandwidth than YUV and requires conversions: only use it if your
     *   processing really needs RGB.
     */
    val pixelFormat: TargetVideoPixelFormat = TargetVideoPixelFormat.NATIVE,
    /**
     * Delivers smaller, preview-sized buffers, for ML and computer vision workloads where full resolution buffers are
     * unnecessary. Other outputs keep their full resolution.
     */
    val enablePreviewSizedOutputBuffers: Boolean = false,
    /**
     * Allows this output to start later in the pipeline startup, so the preview starts first. iOS only.
     */
    val allowDeferredStart: Boolean = true,
    /**
     * Physically rotates the buffers to the output orientation, so [Frame.orientation] is always
     * [CameraOrientation.UP]. Otherwise, [Frame.orientation] tells the consumer how to rotate the Frame. Rotating
     * buffers has a processing overhead.
     */
    val enablePhysicalBufferRotation: Boolean = false,
    /**
     * Attaches the camera intrinsic matrix to the Frames ([Frame.cameraIntrinsicMatrix]). Only works when video
     * stabilization is off. iOS only.
     */
    val enableCameraMatrixDelivery: Boolean = false,
    /**
     * Drops new Frames arriving while the Frame callback is still running (the default), instead of queueing them,
     * which would grow memory if the processing is slower than the Camera.
     */
    val dropFramesWhileBusy: Boolean = true,
)

/**
 * The [CameraFrameOutput] streams [Frame]s from the Camera synchronously, aka "Frame Processing".
 *
 * Every [Frame] must be disposed as soon as possible, see [Frame]'s lifetime contract.
 *
 * ```kotlin
 * val frameOutput = VisionCamera.createFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV))
 * frameOutput.setOnFrameCallback { frame ->
 *     frame.use {
 *         // processing...
 *     }
 * }
 * ```
 *
 * @see CameraFactory.createFrameOutput
 */
interface CameraFrameOutput : CameraOutput {
    /** The [NativeThread] the Frame callbacks run on. */
    val thread: NativeThread

    /**
     * Sets the callback called on [thread] every time the Camera produces a new [Frame], or removes it with `null`.
     * The callback must dispose the [Frame].
     */
    fun setOnFrameCallback(onFrame: ((frame: Frame) -> Unit)?)

    /**
     * Sets the callback called when a [Frame] has been dropped, often because the Frame callback takes longer than a
     * frame interval, or removes it with `null`.
     */
    fun setOnFrameDroppedCallback(onFrameDropped: ((reason: FrameDroppedReason) -> Unit)?)
}
