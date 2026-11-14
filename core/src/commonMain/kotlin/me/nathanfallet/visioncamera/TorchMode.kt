package me.nathanfallet.visioncamera

/**
 * Represents the setting for the device's torch.
 */
enum class TorchMode {
    /** Keep the flash unit always on. */
    ON,

    /** Keep the flash unit off. It can still fire elsewhere (e.g. for photo capture). */
    OFF,
}
