package me.nathanfallet.visioncamera.location

import me.nathanfallet.visioncamera.Location

/**
 * The accuracy of a [Location].
 */
enum class LocationAccuracy {
    /** Typically ~3-10m accuracy. Uses GPS + WiFi + cell + sensors. */
    HIGH,

    /** Typically ~10-50m accuracy. Uses WiFi + cell. */
    BALANCED,

    /** Typically ~100-1000m accuracy. Uses cell. */
    LOW,
}
