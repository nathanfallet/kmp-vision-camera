package me.nathanfallet.visioncamera

/**
 * The type of a vendor-specific [CameraExtension].
 */
enum class CameraExtensionType {
    /** Bokeh (portrait) effect. */
    BOKEH,

    /** HDR processing. */
    HDR,

    /** Night mode. */
    NIGHT,

    /** Face retouching. */
    FACE_RETOUCH,

    /** Automatically selects the best extension for the scene. */
    AUTO,
}

/**
 * A vendor-specific Camera extension, such as Night mode or Bokeh.
 *
 * @see CameraDeviceFactory.getSupportedExtensions
 */
interface CameraExtension {
    /** The type of this extension. */
    val type: CameraExtensionType

    /** Whether this extension supports streaming frames (e.g. to a frame output) while enabled. */
    val supportsFrameStreaming: Boolean
}
