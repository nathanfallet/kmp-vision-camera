package me.nathanfallet.visioncamera

/**
 * Represents the exposure mode of a [CameraDevice].
 */
enum class ExposureMode {
    /** The exposure is locked at its current value and does not adapt to scene changes. */
    LOCKED,

    /** The exposure is adjusted once to a suitable value for the current scene. */
    AUTO_EXPOSURE,

    /** The exposure is continuously adjusted as the scene changes. */
    CONTINUOUS_AUTO_EXPOSURE,

    /** The exposure is manually controlled via custom ISO and shutter speed. */
    CUSTOM,
}
