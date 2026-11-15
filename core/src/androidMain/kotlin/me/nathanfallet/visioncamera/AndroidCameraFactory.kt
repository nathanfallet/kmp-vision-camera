package me.nathanfallet.visioncamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance

/**
 * The Android [CameraFactory], backed by CameraX.
 */
@Suppress("TooManyFunctions")
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

    override fun createPhotoOutput(options: PhotoOutputOptions): CameraPhotoOutput = AndroidPhotoOutput(options)

    override fun createVideoOutput(options: VideoOutputOptions): CameraVideoOutput = AndroidVideoOutput(options)

    override fun createZoomGestureController(): ZoomGestureController = AndroidZoomGestureController()

    override fun createTapToFocusGestureController(): TapToFocusGestureController =
        AndroidTapToFocusGestureController()

    override fun createFrameOutput(options: FrameOutputOptions): CameraFrameOutput = AndroidFrameOutput(options)

    override fun createDepthFrameOutput(options: DepthFrameOutputOptions): CameraDepthFrameOutput =
        AndroidDepthFrameOutput(options)

    override fun createObjectOutput(options: ObjectOutputOptions): CameraObjectOutput =
        throw VisionCameraException("CameraObjectOutput is not available on Android!")

    override fun createOutputSynchronizer(outputs: List<CameraOutput>): CameraOutputSynchronizer =
        throw VisionCameraException("OutputSynchronizer is not available on Android!")

    override fun createOrientationManager(orientationSource: OrientationSource): OrientationManager =
        when (orientationSource) {
            OrientationSource.INTERFACE -> AndroidInterfaceOrientationManager()
            OrientationSource.DEVICE -> AndroidDeviceOrientationManager()
        }

    override fun createFrameRenderer(): FrameRenderer = AndroidFrameRenderer()

    override fun createNormalizedMeteringPoint(x: Double, y: Double, size: Double?): MeteringPoint {
        val meteringPoint = if (size != null) {
            normalizedMeteringPointFactory.createPoint(x.toFloat(), y.toFloat(), size.toFloat())
        } else {
            normalizedMeteringPointFactory.createPoint(x.toFloat(), y.toFloat())
        }
        return AndroidMeteringPoint(x, y, size, meteringPoint)
    }

    private companion object {
        val normalizedMeteringPointFactory by lazy { SurfaceOrientedMeteringPointFactory(1f, 1f) }
    }
}
