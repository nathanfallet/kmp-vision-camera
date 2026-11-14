package me.nathanfallet.visioncamera

/**
 * Represents an output of a [CameraSession], for example a preview, a photo or a video output.
 *
 * @see CameraOutputConfiguration
 */
interface CameraOutput {
    /** The [MediaType] of this output. */
    val mediaType: MediaType

    /** The orientation of the content produced by this output. */
    var outputOrientation: CameraOrientation

    /** The resolution this output is currently streaming at, or `null` if it is not connected yet. */
    val currentResolution: Size?
}

/**
 * An output that streams the Camera feed to a preview view.
 *
 * @see CameraFactory.createPreviewOutput
 */
interface CameraPreviewOutput : CameraOutput
