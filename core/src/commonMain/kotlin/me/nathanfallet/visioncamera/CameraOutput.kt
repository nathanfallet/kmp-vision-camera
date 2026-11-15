package me.nathanfallet.visioncamera

/**
 * Represents an output of a [CameraSession], for example a preview, a photo or a video output.
 *
 * An output holds native resources (threads, analyzers, delegates...), which are released when it is garbage
 * collected or, eagerly, when it is [dispose]d. It implements [AutoCloseable], so it can be used with `use { }`.
 *
 * @see CameraOutputConfiguration
 */
interface CameraOutput : AutoCloseable {
    /** The [MediaType] of this output. */
    val mediaType: MediaType

    /** The orientation of the content produced by this output. */
    var outputOrientation: CameraOrientation

    /** The resolution this output is currently streaming at, or `null` if it is not connected yet. */
    val currentResolution: Size?

    /**
     * Eagerly disposes the native resources this output holds (its threads, analyzers, delegates, sounds...), like
     * VisionCamera's `HybridObject.dispose()`.
     *
     * After calling [dispose], this output can no longer be used: remove it from its [CameraSession] first. Calling it
     * more than once is a no-op.
     *
     * Calling it is **not** required, as unreferenced outputs are garbage collected (that is what VisionCamera's hooks
     * rely on). Eagerly disposing an output is purely optional, and should be done with caution.
     */
    fun dispose()

    /**
     * Same as [dispose], to use a [CameraOutput] with `use { }`.
     */
    override fun close() {
        dispose()
    }
}

/**
 * An output that streams the Camera feed to a preview view.
 *
 * @see CameraFactory.createPreviewOutput
 */
interface CameraPreviewOutput : CameraOutput
