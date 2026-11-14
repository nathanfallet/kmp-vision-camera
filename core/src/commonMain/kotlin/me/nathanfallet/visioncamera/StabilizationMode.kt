package me.nathanfallet.visioncamera

/**
 * Specifies a target stabilization mode: either a specific [StabilizationMode] (which has to be supported by the
 * [CameraDevice]), [TargetStabilizationMode.Auto] or [TargetStabilizationMode.Off].
 */
sealed interface TargetStabilizationMode {
    /** Automatically chooses a suitable [StabilizationMode]. */
    data object Auto : TargetStabilizationMode

    /** Disables stabilization entirely. */
    data object Off : TargetStabilizationMode
}

/**
 * Specifies the stabilization algorithm/mode to use.
 *
 * Most software algorithms reduce the visible field of view to account for rapid movements, and some modes may
 * introduce additional latency by buffering frames.
 */
enum class StabilizationMode : TargetStabilizationMode {
    /** General-purpose stabilization, typically best for recorded video. */
    STANDARD,

    /** Stronger cinematic-style smoothing. */
    CINEMATIC,

    /** An even stronger cinematic-style stabilization mode. */
    CINEMATIC_EXTENDED,

    /** Prioritizes stabilization for the live preview experience. */
    PREVIEW_OPTIMIZED,

    /** The strongest cinematic-style stabilization mode. */
    CINEMATIC_EXTENDED_ENHANCED,

    /** Prioritizes lower stabilization processing latency. */
    LOW_LATENCY,
}
