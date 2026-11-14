package me.nathanfallet.visioncamera

/**
 * Represents the focus mode of a [CameraDevice].
 */
enum class FocusMode {
    /** The lens position is locked at its current value and does not adapt to scene changes. */
    LOCKED,

    /** The lens is adjusted once to focus the current scene. */
    AUTO_FOCUS,

    /** The lens is continuously adjusted as the scene changes. */
    CONTINUOUS_AUTO_FOCUS,
}
