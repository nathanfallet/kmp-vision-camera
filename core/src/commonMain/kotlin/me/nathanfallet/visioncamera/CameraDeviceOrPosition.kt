package me.nathanfallet.visioncamera

/**
 * Either a concrete [CameraDevice], or a [TargetCameraPosition] to automatically select the default
 * [CameraDevice] at the given position.
 *
 * @see CameraSessionConnection.input
 */
sealed interface CameraDeviceOrPosition
