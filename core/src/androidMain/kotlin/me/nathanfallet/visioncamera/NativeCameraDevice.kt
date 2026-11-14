package me.nathanfallet.visioncamera

import androidx.camera.core.CameraInfo

/**
 * A [CameraDevice] backed by a CameraX [CameraInfo].
 */
interface NativeCameraDevice : CameraDevice {
    /** The CameraX [CameraInfo] of this device. */
    val cameraInfo: CameraInfo
}
