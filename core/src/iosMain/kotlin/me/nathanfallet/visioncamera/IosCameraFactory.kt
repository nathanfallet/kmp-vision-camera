package me.nathanfallet.visioncamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureMultiCamSession
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSBundle
import kotlin.coroutines.resume

/**
 * The iOS [CameraFactory], backed by AVFoundation (port of V5's `HybridCameraFactory.swift`).
 */
internal class IosCameraFactory : CameraFactory {
    override val cameraPermissionStatus: PermissionStatus
        get() = permissionStatusOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo))

    override val microphonePermissionStatus: PermissionStatus
        get() = permissionStatusOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio))

    override suspend fun requestCameraPermission(): Boolean = requestPermission(
        mediaType = AVMediaTypeVideo,
        usageDescriptionKey = "NSCameraUsageDescription",
        name = "Camera",
    )

    override suspend fun requestMicrophonePermission(): Boolean = requestPermission(
        mediaType = AVMediaTypeAudio,
        usageDescriptionKey = "NSMicrophoneUsageDescription",
        name = "Microphone",
    )

    private suspend fun requestPermission(mediaType: String?, usageDescriptionKey: String, name: String): Boolean {
        if (NSBundle.mainBundle.objectForInfoDictionaryKey(usageDescriptionKey) == null) {
            throw VisionCameraException(
                "Cannot request $name Permission - Your Info.plist does not contain a `$usageDescriptionKey` key!",
            )
        }
        return suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
                continuation.resume(granted)
            }
        }
    }

    override val supportsMultiCamSessions: Boolean
        get() = AVCaptureMultiCamSession.multiCamSupported

    override suspend fun createCameraSession(enableMultiCam: Boolean): CameraSession {
        if (enableMultiCam && !supportsMultiCamSessions) {
            throw VisionCameraException(
                "Failed to create CameraSession - this device does not support multi-cam sessions! " +
                    "(See `supportsMultiCamSessions`)",
            )
        }
        return IosCameraSession(enableMultiCam)
    }

    override suspend fun resolveConstraints(
        device: CameraDevice,
        outputConfigurations: List<CameraOutputConfiguration>,
        constraints: List<Constraint>,
        requiresMultiCam: Boolean,
    ): CameraSessionConfig = withContext(Dispatchers.Default) {
        val resolved = ConstraintResolver.resolveConstraints(
            device = device,
            constraints = constraints,
            outputs = outputConfigurations.map { it.output },
            isMultiCam = requiresMultiCam,
        )
        IosCameraSessionConfig(
            negotiatedFormat = resolved.negotiatedFormat,
            enabledConstraints = resolved.enabledConstraints,
        )
    }

    override suspend fun createDeviceFactory(): CameraDeviceFactory = IosCameraDeviceFactory()

    override fun createPreviewOutput(): CameraPreviewOutput = IosCameraPreviewOutput()

    override fun createPhotoOutput(options: PhotoOutputOptions): CameraPhotoOutput = IosCameraPhotoOutput(options)

    override fun createVideoOutput(options: VideoOutputOptions): CameraVideoOutput =
        if (options.enablePersistentRecorder) {
            // On iOS, the `AVCaptureMovieFileOutput` does not allow flipping the Camera while recording, which is a
            // feature we call "enablePersistentRecorder". So if the user plans on using persistent recordings, we
            // need to create a custom video output that uses `AVCaptureVideoDataOutput` + `AVAssetWriter` instead.
            IosCameraVideoFrameOutput(options)
        } else {
            IosCameraVideoOutput(options)
        }

    override fun createZoomGestureController(): ZoomGestureController = IosZoomGestureController()

    override fun createTapToFocusGestureController(): TapToFocusGestureController = IosTapToFocusGestureController()

    override fun createFrameOutput(options: FrameOutputOptions): CameraFrameOutput = IosCameraFrameOutput(options)

    override fun createDepthFrameOutput(options: DepthFrameOutputOptions): CameraDepthFrameOutput =
        IosCameraDepthFrameOutput(options)

    override fun createObjectOutput(options: ObjectOutputOptions): CameraObjectOutput = IosCameraObjectOutput(options)

    override fun createOutputSynchronizer(outputs: List<CameraOutput>): CameraOutputSynchronizer =
        IosCameraOutputSynchronizer(outputs)

    override fun createOrientationManager(orientationSource: OrientationSource): OrientationManager =
        when (orientationSource) {
            OrientationSource.INTERFACE -> InterfaceOrientationManager()
            OrientationSource.DEVICE -> DeviceOrientationManager()
        }

    override fun createFrameRenderer(): FrameRenderer = IosFrameRenderer()

    override fun createNormalizedMeteringPoint(x: Double, y: Double, size: Double?): MeteringPoint =
        IosMeteringPoint.normalized(x = x, y = y, size = size ?: IosMeteringPoint.DEFAULT_SIZE)
}
