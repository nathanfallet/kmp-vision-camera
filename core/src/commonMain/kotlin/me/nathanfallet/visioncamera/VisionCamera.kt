package me.nathanfallet.visioncamera

/**
 * The entry point of KMP Vision Camera: the platform's [CameraFactory].
 *
 * - On Android, it is backed by CameraX (and Camera2 interop), and initialized automatically with the application
 *   context.
 * - On iOS, it is backed by AVFoundation.
 */
object VisionCamera : CameraFactory by createPlatformCameraFactory()

/**
 * Creates the [CameraFactory] of the current platform.
 */
internal expect fun createPlatformCameraFactory(): CameraFactory
