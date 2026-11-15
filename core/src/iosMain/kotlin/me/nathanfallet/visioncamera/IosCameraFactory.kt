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
