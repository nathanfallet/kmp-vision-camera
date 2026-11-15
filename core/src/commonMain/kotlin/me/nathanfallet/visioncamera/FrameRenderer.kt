package me.nathanfallet.visioncamera

/**
 * Renders [Frame]s into a target. For example, a `FrameRendererView` connected to a [FrameRenderer] renders its
 * [Frame]s on screen. It could also feed a custom video recorder.
 *
 * @see CameraFactory.createFrameRenderer
 */
interface FrameRenderer {
    /** Renders the given [frame], synchronously. */
    fun renderFrame(frame: Frame)
}
