package me.nathanfallet.visioncamera

/**
 * Creates the iOS [CameraFactory], backed by AVFoundation.
 */
internal actual fun createPlatformCameraFactory(): CameraFactory = IosCameraFactory()
