package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDevicePositionUnspecified
import platform.AVFoundation.AVCaptureDeviceWasConnectedNotification
import platform.AVFoundation.AVCaptureDeviceWasDisconnectedNotification
import platform.AVFoundation.setUserPreferredCamera
import platform.AVFoundation.userPreferredCamera
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

/**
 * The iOS [CameraDeviceFactory], backed by an [AVCaptureDeviceDiscoverySession] over all device types (port of V5's
 * `HybridCameraDeviceFactory.swift`).
 */
internal class IosCameraDeviceFactory : CameraDeviceFactory {
    private val discoverySession = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = allAVCaptureDeviceTypes,
        mediaType = null,
        position = AVCaptureDevicePositionUnspecified,
    )

    private val discoveredDevices: List<AVCaptureDevice>
        get() = discoverySession.devices.filterIsInstance<AVCaptureDevice>()

    override val cameraDevices: List<CameraDevice>
        get() = discoveredDevices.map { IosCameraDevice(it) }

    override val supportedMultiCamDeviceCombinations: List<List<CameraDevice>>
        get() = discoverySession.supportedMultiCamDeviceSets
            .filterIsInstance<Set<*>>()
            .map { devices -> devices.filterIsInstance<AVCaptureDevice>().map { IosCameraDevice(it) } }

    override var userPreferredCamera: CameraDevice?
        get() {
            if (!isIOSAtLeast(major = 17)) return null
            return AVCaptureDevice.userPreferredCamera?.let { IosCameraDevice(it) }
        }
        set(value) {
            if (!isIOSAtLeast(major = 17)) return
            val device = value as? IosCameraDevice ?: return
            AVCaptureDevice.setUserPreferredCamera(device.device)
        }

    /**
     * V5 observes `discoverySession.devices` via KVO. Kotlin/Native cannot override `observeValueForKeyPath`, so we
     * listen to the device connected/disconnected notifications instead, and only notify when the discovered devices
     * actually changed.
     */
    override fun addOnCameraDevicesChangedListener(
        listener: (newDevices: List<CameraDevice>) -> Unit,
    ): ListenerSubscription {
        val center = NSNotificationCenter.defaultCenter
        var lastDeviceIds = discoveredDevices.map { it.uniqueID }
        val onChanged: (NSNotification?) -> Unit = {
            val newDevices = discoveredDevices
            val newDeviceIds = newDevices.map { it.uniqueID }
            if (newDeviceIds != lastDeviceIds) {
                Logger.info("Devices changed! ${lastDeviceIds.size} -> ${newDeviceIds.size}")
                lastDeviceIds = newDeviceIds
                listener(newDevices.map { IosCameraDevice(it) })
            }
        }
        val observers = listOf(AVCaptureDeviceWasConnectedNotification, AVCaptureDeviceWasDisconnectedNotification)
            .map { name ->
                center.addObserverForName(
                    name = name,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                    usingBlock = onChanged,
                )
            }
        return ListenerSubscription { observers.forEach { center.removeObserver(it) } }
    }

    override fun getCameraForId(id: String): CameraDevice? =
        AVCaptureDevice.deviceWithUniqueID(id)?.let { IosCameraDevice(it) }

    // There are no Camera Extensions on iOS.
    override suspend fun getSupportedExtensions(camera: CameraDevice): List<CameraExtension> = emptyList()

    override fun getDefaultCamera(position: TargetCameraPosition): CameraDevice? =
        defaultCaptureDevice(position)?.let { IosCameraDevice(it) }
}
