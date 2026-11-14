package me.nathanfallet.visioncamera

/**
 * Represents the white balance mode of a [CameraDevice].
 */
enum class WhiteBalanceMode {
    /** The white balance is locked at its current value and does not adapt to scene changes. */
    LOCKED,

    /** The white balance is adjusted once to match the current scene. */
    AUTO_WHITE_BALANCE,

    /** The white balance is continuously adjusted as the scene changes. */
    CONTINUOUS_AUTO_WHITE_BALANCE,
}

/**
 * Represents the per-channel gains applied for white balance correction.
 */
data class WhiteBalanceGains(
    /** The gain applied to the red channel. */
    val redGain: Double,
    /** The gain applied to the blue channel. */
    val blueGain: Double,
    /** The gain applied to the green channel. */
    val greenGain: Double,
)

/**
 * Represents a white balance setting expressed as a color temperature and tint offset.
 */
data class WhiteBalanceTemperatureAndTint(
    /** The color temperature in Kelvin (e.g. `5500` for daylight, `3200` for tungsten). */
    val temperature: Double,
    /** The green/magenta tint offset. Positive values shift towards magenta, negative towards green. */
    val tint: Double,
)
