package me.nathanfallet.visioncamera

/**
 * Represents the source of a [CameraOrientation].
 */
enum class OrientationSource {
    /** Uses the UI's orientation. If screen rotation is locked, the output orientation is also locked. */
    INTERFACE,

    /** Uses the physical device's orientation, even if screen rotation is locked. */
    DEVICE,
}
