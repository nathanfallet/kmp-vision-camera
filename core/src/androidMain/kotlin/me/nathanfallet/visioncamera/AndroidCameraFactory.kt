package me.nathanfallet.visioncamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance

/**
 * The Android [CameraFactory], backed by CameraX.
 */
internal class AndroidCameraFactory : CameraFactory {
    private val context: Context
        get() = AndroidContext.context

    private suspend fun cameraProvider(): ProcessCameraProvider = ProcessCameraProvider.awaitInstance(context)

    override val cameraPermissionStatus: PermissionStatus
        get() = context.getPermissionStatus(Manifest.permission.CAMERA)

    override val microphonePermissionStatus: PermissionStatus
        get() = context.getPermissionStatus(Manifest.permission.RECORD_AUDIO)

    override suspend fun requestCameraPermission(): Boolean = context.requestPermission(Manifest.permission.CAMERA)

    override suspend fun requestMicrophonePermission(): Boolean =
        context.requestPermission(Manifest.permission.RECORD_AUDIO)

    override val supportsMultiCamSessions: Boolean
        @SuppressLint("InlinedApi")
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)

    override suspend fun createCameraSession(enableMultiCam: Boolean): CameraSession {
        if (enableMultiCam && !supportsMultiCamSessions) {
            throw VisionCameraException("Multi-Cam sessions are not supported on this device!")
        }
        return AndroidCameraSession(cameraProvider(), enableMultiCam)
    }

    override suspend fun resolveConstraints(
        device: CameraDevice,
        outputConfigurations: List<CameraOutputConfiguration>,
        constraints: List<Constraint>,
        requiresMultiCam: Boolean,
    ): CameraSessionConfig {
        val nativeDevice = device as? NativeCameraDevice
            ?: throw VisionCameraException("The given `device` was not created by VisionCamera!")
        return ConstraintResolver.resolveConstraints(nativeDevice.cameraInfo, outputConfigurations, constraints)
            .toCameraSessionConfig()
    }

    override suspend fun createDeviceFactory(): CameraDeviceFactory = AndroidCameraDeviceFactory(cameraProvider())

    override fun createPreviewOutput(): CameraPreviewOutput = AndroidPreviewOutput()

    // Phase 1 features below are not implemented on this platform yet.

    override fun createPhotoOutput(options: PhotoOutputOptions): CameraPhotoOutput =
        notImplementedYet("createPhotoOutput")

    override fun createVideoOutput(options: VideoOutputOptions): CameraVideoOutput =
        notImplementedYet("createVideoOutput")

    override fun createZoomGestureController(): ZoomGestureController = notImplementedYet("createZoomGestureController")

    override fun createTapToFocusGestureController(): TapToFocusGestureController =
        notImplementedYet("createTapToFocusGestureController")

    override fun createNormalizedMeteringPoint(x: Double, y: Double, size: Double?): MeteringPoint =
        notImplementedYet("createNormalizedMeteringPoint")
}
