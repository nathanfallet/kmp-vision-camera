package me.nathanfallet.visioncamera

import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureAutoFocusSystem
import platform.AVFoundation.AVCaptureAutoFocusSystemContrastDetection
import platform.AVFoundation.AVCaptureAutoFocusSystemPhaseDetection
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDevicePositionUnspecified
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInLiDARDepthCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTelephotoCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTrueDepthCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInUltraWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureDeviceTypeContinuityCamera
import platform.AVFoundation.AVCaptureDeviceTypeExternal
import platform.AVFoundation.AVCaptureTorchMode
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVMediaTypeDepthData
import platform.AVFoundation.AVMediaTypeMetadataObject
import platform.AVFoundation.AVMediaTypeVideo

// Port of V5's AV+PermissionStatus.swift, AV+DeviceType.swift, AV+CameraPosition.swift, AV+MediaType.swift,
// AV+TorchMode.swift and AV+AutoFocusSystem.swift.

/** Converts an [AVAuthorizationStatus] to a [PermissionStatus]. */
internal fun permissionStatusOf(status: AVAuthorizationStatus): PermissionStatus = when (status) {
    AVAuthorizationStatusNotDetermined -> PermissionStatus.NOT_DETERMINED
    AVAuthorizationStatusRestricted -> PermissionStatus.RESTRICTED
    AVAuthorizationStatusDenied -> PermissionStatus.DENIED
    AVAuthorizationStatusAuthorized -> PermissionStatus.AUTHORIZED
    else -> error("Received unknown AVAuthorizationStatus! $status")
}

/** Converts an `AVCaptureDeviceType` to a [DeviceType]. */
internal fun deviceTypeOf(type: String?): DeviceType = deviceTypesByAVCaptureDeviceType[type] ?: run {
    Logger.error("Unknown DeviceType: $type!")
    DeviceType.UNKNOWN
}

/** All `AVCaptureDeviceType`s available on this iOS version, mapped to their [DeviceType]. */
private val deviceTypesByAVCaptureDeviceType: Map<String?, DeviceType> by lazy {
    buildMap {
        put(AVCaptureDeviceTypeBuiltInWideAngleCamera, DeviceType.WIDE_ANGLE)
        put(AVCaptureDeviceTypeBuiltInUltraWideCamera, DeviceType.ULTRA_WIDE_ANGLE)
        put(AVCaptureDeviceTypeBuiltInTelephotoCamera, DeviceType.TELEPHOTO)
        put(AVCaptureDeviceTypeBuiltInDualCamera, DeviceType.DUAL)
        put(AVCaptureDeviceTypeBuiltInDualWideCamera, DeviceType.DUAL_WIDE)
        put(AVCaptureDeviceTypeBuiltInTripleCamera, DeviceType.TRIPLE)
        put(AVCaptureDeviceTypeBuiltInTrueDepthCamera, DeviceType.TRUE_DEPTH)
        if (isIOSAtLeast(major = 15, minor = 4)) {
            put(AVCaptureDeviceTypeBuiltInLiDARDepthCamera, DeviceType.LIDAR_DEPTH)
        }
        if (isIOSAtLeast(major = 17)) {
            put(AVCaptureDeviceTypeContinuityCamera, DeviceType.CONTINUITY)
            put(AVCaptureDeviceTypeExternal, DeviceType.EXTERNAL)
        }
    }
}

/**
 * All `AVCaptureDeviceType`s, the equivalent of V5's `AVCaptureDevice.DeviceType.all`. This list should be kept up
 * to date by just adding new device types here.
 */
internal val allAVCaptureDeviceTypes: List<String>
    get() = buildList {
        add(AVCaptureDeviceTypeBuiltInWideAngleCamera)
        add(AVCaptureDeviceTypeBuiltInUltraWideCamera)
        add(AVCaptureDeviceTypeBuiltInTelephotoCamera)
        add(AVCaptureDeviceTypeBuiltInDualCamera)
        add(AVCaptureDeviceTypeBuiltInDualWideCamera)
        add(AVCaptureDeviceTypeBuiltInTripleCamera)
        if (isIOSAtLeast(major = 17)) {
            add(AVCaptureDeviceTypeContinuityCamera)
            add(AVCaptureDeviceTypeExternal)
        }
        if (isIOSAtLeast(major = 15, minor = 4)) {
            add(AVCaptureDeviceTypeBuiltInLiDARDepthCamera)
        }
        add(AVCaptureDeviceTypeBuiltInTrueDepthCamera)
    }.filterNotNull()

/** Converts an [AVCaptureDevicePosition] to a [CameraPosition]. */
internal fun cameraPositionOf(position: AVCaptureDevicePosition): CameraPosition = when (position) {
    AVCaptureDevicePositionUnspecified -> CameraPosition.UNSPECIFIED
    AVCaptureDevicePositionBack -> CameraPosition.BACK
    AVCaptureDevicePositionFront -> CameraPosition.FRONT
    else -> {
        Logger.error("Unknown Device Position: $position")
        CameraPosition.UNSPECIFIED
    }
}

/** Converts an `AVMediaType` to a [MediaType]. */
internal fun mediaTypeOf(type: String?): MediaType = when (type) {
    AVMediaTypeVideo -> MediaType.VIDEO
    AVMediaTypeDepthData -> MediaType.DEPTH
    AVMediaTypeMetadataObject -> MediaType.METADATA
    else -> MediaType.OTHER
}

/** Converts a [MediaType] to an `AVMediaType`. */
internal fun MediaType.toAVMediaType(): String? = when (this) {
    MediaType.VIDEO -> AVMediaTypeVideo
    MediaType.DEPTH -> AVMediaTypeDepthData
    MediaType.METADATA -> AVMediaTypeMetadataObject
    MediaType.OTHER -> throw VisionCameraException("Cannot convert MediaType OTHER to an AVMediaType!")
}

/** Converts an [AVCaptureTorchMode] to a [TorchMode]. `auto` is treated like `off`. */
internal fun torchModeOf(mode: AVCaptureTorchMode): TorchMode = when (mode) {
    AVCaptureTorchModeOn -> TorchMode.ON
    else -> TorchMode.OFF
}

/** Converts an [AVCaptureAutoFocusSystem] to an [AutoFocusSystem]. */
internal fun autoFocusSystemOf(system: AVCaptureAutoFocusSystem): AutoFocusSystem = when (system) {
    AVCaptureAutoFocusSystemContrastDetection -> AutoFocusSystem.CONTRAST_DETECTION
    AVCaptureAutoFocusSystemPhaseDetection -> AutoFocusSystem.PHASE_DETECTION
    else -> AutoFocusSystem.NONE
}
