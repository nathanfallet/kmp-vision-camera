package me.nathanfallet.visioncamera

/**
 * Configuration options for a [CameraDepthFrameOutput].
 *
 * @see CameraFactory.createDepthFrameOutput
 */
data class DepthFrameOutputOptions(
    /** The target Depth resolution, see [FrameOutputOptions.targetResolution]. */
    val targetResolution: Size = CommonResolutions.VGA_16_9,
    /** Whether the platform filters (smooths) the depth data, reducing noise at the cost of some fine detail. */
    val enableFiltering: Boolean = true,
    /** See [FrameOutputOptions.enablePhysicalBufferRotation]. */
    val enablePhysicalBufferRotation: Boolean = false,
    /** See [FrameOutputOptions.dropFramesWhileBusy]. */
    val dropFramesWhileBusy: Boolean = true,
    /** See [FrameOutputOptions.allowDeferredStart]. */
    val allowDeferredStart: Boolean = true,
)

/**
 * The [CameraDepthFrameOutput] streams [Depth] frames from the Camera, on devices supporting depth (whose
 * [CameraDevice.mediaTypes] contain [MediaType.DEPTH]).
 *
 * @see CameraFactory.createDepthFrameOutput
 */
interface CameraDepthFrameOutput : CameraOutput {
    /** The [NativeThread] the Depth callbacks run on. */
    val thread: NativeThread

    /**
     * Sets the callback called on [thread] every time the Camera produces a new [Depth] frame, or removes it with
     * `null`. The callback must dispose the [Depth].
     */
    fun setOnDepthFrameCallback(onDepthFrame: ((depth: Depth) -> Unit)?)

    /** Sets the callback called when a [Depth] frame has been dropped, or removes it with `null`. */
    fun setOnDepthFrameDroppedCallback(onDepthFrameDropped: ((reason: FrameDroppedReason) -> Unit)?)
}
