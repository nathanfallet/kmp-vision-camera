package me.nathanfallet.visioncamera

/**
 * Specifies a single Camera input stream connection streaming into zero or more outputs.
 */
data class CameraSessionConnection(
    /**
     * The input device of this connection, streaming into all [outputs]. Pass a [TargetCameraPosition] to
     * automatically select the default [CameraDevice] at the given position.
     */
    val input: CameraDeviceOrPosition,
    /** All output configurations the [input] device will stream into. */
    val outputs: List<CameraOutputConfiguration>,
    /** Constraints specifically for this connection, ranked by priority. */
    val constraints: List<Constraint> = emptyList(),
    /** The initial [CameraController.zoom] value, later adjustable via [CameraController.setZoom]. */
    val initialZoom: Double? = null,
    /** The initial [CameraController.exposureBias], later adjustable via [CameraController.setExposureBias]. */
    val initialExposureBias: Double? = null,
    /**
     * Called once the [constraints] have been resolved into a valid [CameraSessionConfig], for example to grey out
     * buttons of features that ended up not being enabled.
     */
    val onSessionConfigSelected: ((config: CameraSessionConfig) -> Unit)? = null,
)

/**
 * Specifies options for an output in a [CameraSessionConnection].
 */
data class CameraOutputConfiguration(
    /** The [CameraOutput]. */
    val output: CameraOutput,
    /**
     * Whether the output is mirrored along the vertical axis. [MirrorMode.AUTO] mirrors automatically if the
     * device suggests it, for example on selfie cameras.
     */
    val mirrorMode: MirrorMode = MirrorMode.AUTO,
)

/**
 * Session-wide configuration for a [CameraSession].
 */
data class CameraSessionConfiguration(
    /**
     * If enabled, the device may play haptics and system sounds while the session is active (they may then also
     * be captured in Video recordings). iOS only.
     */
    val allowHapticsAndSystemSoundsPlayback: Boolean = false,
    /**
     * If enabled, the device may keep playing background audio (e.g. from another app) while the session is active
     * (it may then also be captured in Video recordings). iOS only.
     */
    val allowBackgroundAudioPlayback: Boolean = false,
)
