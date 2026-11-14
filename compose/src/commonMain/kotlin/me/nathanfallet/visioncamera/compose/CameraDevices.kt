package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraDeviceFactory
import me.nathanfallet.visioncamera.DeviceFilter
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.VisionCamera
import me.nathanfallet.visioncamera.getCameraDevice

/**
 * Get the [CameraDeviceFactory], or `null` while it is still loading.
 */
@Composable
fun rememberCameraDeviceFactory(): CameraDeviceFactory? =
    produceState<CameraDeviceFactory?>(null) { value = VisionCamera.createDeviceFactory() }.value

/**
 * Get a list of all [CameraDevice]s. It updates when devices change (e.g. a USB Camera is plugged in).
 *
 * It is recommended to use [rememberCameraDevice] if you only need a single [CameraDevice], as a full list of
 * available devices can be heavy.
 */
@Composable
fun rememberCameraDevices(): List<CameraDevice> {
    val deviceFactory = rememberCameraDeviceFactory() ?: return emptyList()
    var devices by remember(deviceFactory) { mutableStateOf(deviceFactory.cameraDevices) }
    DisposableEffect(deviceFactory) {
        val subscription = deviceFactory.addOnCameraDevicesChangedListener { devices = it }
        onDispose { subscription.remove() }
    }
    return devices
}

/**
 * Get the [CameraDevice] that best satisfies the given [filter] at the given [position], or the default
 * [CameraDevice] at that [position] if no [filter] is given. It updates when devices change.
 *
 * ```kotlin
 * val device = rememberCameraDevice(TargetCameraPosition.BACK, DeviceFilter(listOf(DeviceType.WIDE_ANGLE)))
 * ```
 *
 * @return The matching [CameraDevice], or `null` while loading or if there is no Camera at that [position].
 */
@Composable
fun rememberCameraDevice(position: TargetCameraPosition, filter: DeviceFilter? = null): CameraDevice? {
    val deviceFactory = rememberCameraDeviceFactory() ?: return null
    var device by remember(deviceFactory, position, filter) {
        mutableStateOf(getCameraDevice(deviceFactory, position, filter))
    }
    DisposableEffect(deviceFactory, position, filter) {
        val subscription = deviceFactory.addOnCameraDevicesChangedListener {
            val newDevice = getCameraDevice(deviceFactory, position, filter)
            // Only update if the device actually changed.
            if (newDevice?.id != device?.id) device = newDevice
        }
        onDispose { subscription.remove() }
    }
    return device
}
