package me.nathanfallet.visioncamera

/**
 * An error thrown by KMP Vision Camera, for example when a [CameraSession] fails to configure.
 */
open class VisionCameraException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Throws a [VisionCameraException] for a [feature] that is not implemented on this platform yet. */
internal fun notImplementedYet(feature: String): Nothing =
    throw VisionCameraException("$feature is not implemented on this platform yet.")
