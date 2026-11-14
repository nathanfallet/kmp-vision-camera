package me.nathanfallet.visioncamera

/**
 * Represents the target mirroring setting for a Camera Output.
 */
enum class MirrorMode {
    /** Mirrors the Camera, e.g. for front Cameras. */
    ON,

    /** Doesn't mirror anything. */
    OFF,

    /** Automatically mirrors the Camera if the platform recommends it, such as for front Cameras. */
    AUTO,
}
