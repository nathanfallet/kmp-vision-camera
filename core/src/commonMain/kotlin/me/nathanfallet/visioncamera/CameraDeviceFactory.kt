package me.nathanfallet.visioncamera

/**
 * The [CameraDeviceFactory] allows getting [CameraDevice]s, listening to device changes, and querying extensions
 * or preferred Cameras.
 *
 * @see CameraFactory.createDeviceFactory
 */
interface CameraDeviceFactory {
    /**
     * A list of all [CameraDevice]s on this platform.
     *
     * This list may change as devices get plugged in/out (e.g. USB Cameras), become available/unavailable
     * (e.g. Continuity Cameras), or change position (e.g. on foldable phones).
     */
    val cameraDevices: List<CameraDevice>

    /**
     * The user's default preferred camera device, if any. Setting a device persists the preference between apps
     * (iOS only, ignored on Android).
     */
    var userPreferredCamera: CameraDevice?

    /**
     * All [CameraDevice] combinations that are supported in Multi-Cam [CameraSession]s, or an empty list if the
     * platform does not support Multi-Cam sessions.
     *
     * Each inner list contains one or more logical [CameraDevice]s that can be passed directly to
     * [CameraSession.configure].
     */
    val supportedMultiCamDeviceCombinations: List<List<CameraDevice>>

    /**
     * Add a [listener] to be called whenever the available [cameraDevices] change, for example when an external
     * USB Camera gets plugged in or out.
     */
    fun addOnCameraDevicesChangedListener(listener: (newDevices: List<CameraDevice>) -> Unit): ListenerSubscription

    /**
     * Get the [CameraDevice] with the given unique [id], or `null` if no such device exists.
     */
    fun getCameraForId(id: String): CameraDevice?

    /**
     * Get all vendor-specific [CameraExtension]s the given [camera] supports.
     */
    suspend fun getSupportedExtensions(camera: CameraDevice): List<CameraExtension>

    /**
     * Get the platform's default [CameraDevice] at the given [position], if any.
     */
    fun getDefaultCamera(position: TargetCameraPosition): CameraDevice?
}
