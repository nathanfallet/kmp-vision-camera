package me.nathanfallet.visioncamera

/**
 * Represents the target physical position of a [CameraDevice] on the device.
 *
 * A [TargetCameraPosition] can be used anywhere a [CameraDeviceOrPosition] is expected, to automatically select
 * the default [CameraDevice] at that position.
 */
enum class TargetCameraPosition : CameraDeviceOrPosition {
    /** Target Cameras located on the front of the device, typically used for selfies. */
    FRONT,

    /** Target Cameras located on the back of the device, typically used for capturing the scene. */
    BACK,

    /** Target external Cameras (e.g. USB, Continuity Camera). */
    EXTERNAL,
}

/**
 * Represents the physical position of a [CameraDevice] on the device.
 */
enum class CameraPosition {
    /** The Camera is located on the front of the device, typically used for selfies. */
    FRONT,

    /** The Camera is located on the back of the device, typically used for capturing the scene. */
    BACK,

    /** The Camera is an external Camera (e.g. USB, Continuity Camera). */
    EXTERNAL,

    /** The Camera's position is unknown or not applicable. */
    UNSPECIFIED,
}
