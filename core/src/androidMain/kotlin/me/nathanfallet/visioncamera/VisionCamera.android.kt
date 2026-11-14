package me.nathanfallet.visioncamera

internal actual fun createPlatformCameraFactory(): CameraFactory = AndroidCameraFactory()
