package me.nathanfallet.visioncamera

/**
 * Synchronizes 2 or more [CameraOutput]s to align their timestamps, most commonly a [CameraFrameOutput] and a
 * [CameraDepthFrameOutput] so their [Frame]s and [Depth] frames match. iOS only.
 *
 * Once an output is connected to a [CameraOutputSynchronizer], use [setOnFramesCallback] instead of the output's own
 * callback.
 *
 * @see CameraFactory.createOutputSynchronizer
 */
interface CameraOutputSynchronizer {
    /** The [NativeThread] the callbacks run on. */
    val thread: NativeThread

    /** The [CameraOutput]s connected to this synchronizer. */
    val outputs: List<CameraOutput>

    /**
     * Sets the callback called on [thread] with each set of synchronized [Frame]s and [Depth] frames, or removes it
     * with `null`. The callback must dispose every frame.
     */
    fun setOnFramesCallback(onFrames: ((frames: List<FrameOrDepth>) -> Unit)?)

    /** Sets the callback called when a frame of the given [MediaType] has been dropped, or removes it with `null`. */
    fun setOnFrameDroppedCallback(onFrameDropped: ((frameType: MediaType, reason: FrameDroppedReason) -> Unit)?)
}
