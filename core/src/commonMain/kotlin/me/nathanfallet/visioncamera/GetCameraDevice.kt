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

/**
 * Get the [CameraDevice] that best satisfies the given [filter] at the given [position], or the platform's default
 * [CameraDevice] at that [position] if no [filter] is given.
 *
 * If this platform has any Camera at the given [position], this always returns a Camera: the [filter] ranks
 * devices, it never excludes them.
 *
 * ```kotlin
 * val device = getCameraDevice(deviceFactory, TargetCameraPosition.BACK, DeviceFilter(listOf(DeviceType.WIDE_ANGLE)))
 * ```
 */
fun getCameraDevice(
    deviceFactory: CameraDeviceFactory,
    position: TargetCameraPosition,
    filter: DeviceFilter? = null,
): CameraDevice? {
    if (filter?.physicalDevices == null) {
        // No filter was passed, so let's just get the default Camera Device at that position.
        deviceFactory.getDefaultCamera(position)?.let { return it }
    }

    // We have a filter, or there is no default Camera Device: rank all devices at that position.
    // 1. If the filter has all cameras ([ultra-wide, wide, tele]), prefer devices that have all of them.
    // 2. If it has only one ([wide]), prefer devices that only have that one, for faster performance.
    val wanted = filter?.physicalDevices ?: listOf(DeviceType.WIDE_ANGLE)
    val cameraPosition = position.toCameraPosition()
    return deviceFactory.cameraDevices
        .filter { it.position == cameraPosition }
        .maxByOrNull { device ->
            device.physicalDevices.sumOf { physical -> if (physical.type in wanted) 1L else -1L }
        }
}

/**
 * Converts this [TargetCameraPosition] to the matching [CameraPosition].
 */
fun TargetCameraPosition.toCameraPosition(): CameraPosition = when (this) {
    TargetCameraPosition.FRONT -> CameraPosition.FRONT
    TargetCameraPosition.BACK -> CameraPosition.BACK
    TargetCameraPosition.EXTERNAL -> CameraPosition.EXTERNAL
}
