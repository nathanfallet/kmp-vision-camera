package me.nathanfallet.visioncamera

/**
 * Represents the auto-focus algorithm used by the [CameraDevice], either while continuously keeping a scene in
 * focus (via auto-3A / AE/AF/AWB), or for a metering action.
 */
enum class AutoFocusSystem {
    /** The [CameraDevice] does not support auto-focus at all. */
    NONE,

    /** Finds focus by adjusting the lens until image contrast is highest. Generally slower than phase-detection. */
    CONTRAST_DETECTION,

    /** Finds focus by using phase information to predict the correct lens position. Fastest when available. */
    PHASE_DETECTION,
}
