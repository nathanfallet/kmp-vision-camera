package me.nathanfallet.visioncamera

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Specifies the metering mode, also known as the 3A operation.
 */
enum class MeteringMode {
    /** Auto-Exposure, requires [CameraDevice.supportsExposureMetering]. */
    AE,

    /** Auto-Focus, requires [CameraDevice.supportsFocusMetering]. */
    AF,

    /** Auto-White-Balance, requires [CameraDevice.supportsWhiteBalanceMetering]. */
    AWB,
}

/**
 * Configures the responsiveness of a AE/AF/AWB focus operation.
 */
enum class FocusResponsiveness {
    /** Steadily focuses to the point of interest without disrupting. Most desirable for filming videos. */
    STEADY,

    /** Focuses to the point of interest as fast as possible, possibly resetting focus. */
    SNAPPY,
}

/**
 * Configures the adaptiveness of the AE/AF/AWB pipeline after a metering operation has settled.
 */
enum class SceneAdaptiveness {
    /** Continuously keeps the target point in focus, adapting AE/AF/AWB values if the subject area changes. */
    CONTINUOUS,

    /** Keeps the AE/AF/AWB values locked in-place after the metering operation has settled. */
    LOCKED,
}

/**
 * Options for a focus metering operation.
 */
data class FocusOptions(
    /** The responsiveness of the metering operation. */
    val responsiveness: FocusResponsiveness = FocusResponsiveness.SNAPPY,
    /** The adaptiveness to changes in the scene. */
    val adaptiveness: SceneAdaptiveness = SceneAdaptiveness.CONTINUOUS,
    /**
     * The metering modes to run this focus operation on, or `null` to enable every [MeteringMode] the device
     * supports (ideally 3A).
     */
    val modes: List<MeteringMode>? = null,
    /**
     * If set, the focus automatically resets itself to continuous auto focus after it has been stable for this
     * duration. If `null`, the focus stays locked until it is reset or another focus operation starts.
     */
    val autoResetAfter: Duration? = 5.seconds,
)
