package me.nathanfallet.visioncamera

/**
 * A filter to find the best matching [CameraDevice] with [getCameraDevice].
 */
data class DeviceFilter(
    /**
     * The desired physical devices your camera device should have, e.g. `[WIDE_ANGLE]` for a simple device that
     * starts up faster, or `[ULTRA_WIDE_ANGLE, WIDE_ANGLE, TELEPHOTO]` for a virtual device that can switch between
     * lenses while zooming.
     */
    val physicalDevices: List<DeviceType>? = null,
)
