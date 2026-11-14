package me.nathanfallet.visioncamera

/**
 * An error thrown by KMP Vision Camera, for example when a [CameraSession] fails to configure.
 */
open class VisionCameraException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
