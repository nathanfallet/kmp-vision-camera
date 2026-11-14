package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceTypeContinuityCamera
import platform.AVFoundation.AVCaptureDeviceTypeExternal
import platform.AVFoundation.AVCaptureExposureModeAutoExpose
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureExposureModeCustom
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureWhiteBalanceModeAutoWhiteBalance
import platform.AVFoundation.AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
import platform.AVFoundation.companionDeskViewCamera
import platform.AVFoundation.continuityCamera
import platform.AVFoundation.deviceType
import platform.AVFoundation.geometricDistortionCorrectionSupported
import platform.AVFoundation.hasFlash
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isWhiteBalanceModeSupported
import platform.AVFoundation.lensAperture
import platform.AVFoundation.lockingFocusWithCustomLensPositionSupported
import platform.AVFoundation.lockingWhiteBalanceWithCustomDeviceGainsSupported
import platform.AVFoundation.lowLightBoostSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.maxWhiteBalanceGain
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.nominalFocalLengthIn35mmFilm
import platform.AVFoundation.position
import platform.AVFoundation.smoothAutoFocusSupported
import platform.AVFoundation.virtualDevice
import platform.AVFoundation.virtualDeviceSwitchOverVideoZoomFactors
import platform.Foundation.NSNumber

/**
 * The iOS [CameraDevice], wrapping an [AVCaptureDevice] (port of V5's `HybridCameraDevice.swift`).
 */
@Suppress("TooManyFunctions")
internal class IosCameraDevice(val device: AVCaptureDevice) : CameraDevice {
    // Device Metadata

    override val id: String get() = device.uniqueID
    override val modelID: String get() = device.modelID
    override val localizedName: String get() = device.localizedName
    override val manufacturer: String get() = device.manufacturer
    override val type: DeviceType get() = deviceTypeOf(device.deviceType)

    override val position: CameraPosition
        get() {
            // On iOS, Position "external" is for some reason reflected on the .deviceType, not on .position.
            val deviceType = device.deviceType
            val isExternal = isIOSAtLeast(major = 17) &&
                (deviceType == AVCaptureDeviceTypeExternal || deviceType == AVCaptureDeviceTypeContinuityCamera)
            return if (isExternal) CameraPosition.EXTERNAL else cameraPositionOf(device.position)
        }

    override val physicalDevices: List<CameraDevice> by lazy {
        device.constituentCaptureDevices.map { IosCameraDevice(it) }
    }

    override val isVirtualDevice: Boolean get() = device.virtualDevice

    // Output Capabilities

    override val supportedPixelFormats: List<PixelFormat> by lazy {
        val videoFormats = device.deviceFormats
        val depthFormats = videoFormats.flatMap { it.depthDataFormats }
        (videoFormats + depthFormats).map { it.pixelFormat }.distinct()
    }

    override fun getSupportedResolutions(outputStreamType: OutputStreamType): List<Size> {
        val streamType = when (outputStreamType) {
            OutputStreamType.PHOTO -> StreamType.PHOTO
            OutputStreamType.VIDEO, OutputStreamType.STREAM -> StreamType.VIDEO
            OutputStreamType.DEPTH_PHOTO -> StreamType.DEPTH_PHOTO
            OutputStreamType.DEPTH_STREAM -> StreamType.DEPTH_VIDEO
        }
        return device.deviceFormats.flatMap { it.supportedResolutions(streamType) }.distinct()
    }

    override fun supportsOutput(output: CameraOutput): Boolean = when (output) {
        is NativeCameraOutput -> device.deviceFormats.any { it.supportsOutput(output.output) }
        // Every video device can stream to a preview layer.
        is NativePreviewViewOutput -> device.hasMediaType(MediaType.VIDEO.toAVMediaType())
        else -> false
    }

    // Capabilities

    override val supportsPhotoHDR: Boolean = false

    override val supportedVideoDynamicRanges: List<DynamicRange> by lazy {
        device.deviceFormats
            .flatMap { format ->
                format.colorSpaces.map { colorSpace ->
                    DynamicRange(
                        bitDepth = format.bitDepth,
                        colorSpace = colorSpaceOf(colorSpace),
                        colorRange = format.colorRange,
                    )
                }
            }
            .distinct()
    }

    override val supportedFPSRanges: List<Range> by lazy {
        device.deviceFormats.flatMap { it.frameRateRanges }.map { it.toRange() }.distinct()
    }

    override fun supportsFPS(fps: Double): Boolean = supportedFPSRanges.any { fps in it }

    override fun supportsVideoStabilizationMode(videoStabilizationMode: TargetStabilizationMode): Boolean {
        val stabilizationMode = videoStabilizationMode.toAVCaptureVideoStabilizationMode()
        return device.deviceFormats.any { it.isVideoStabilizationModeSupported(stabilizationMode) }
    }

    // Video and Preview stabilization flows through the same connection on iOS.
    override fun supportsPreviewStabilizationMode(previewStabilizationMode: TargetStabilizationMode): Boolean =
        supportsVideoStabilizationMode(previewStabilizationMode)

    override val supportsPreviewImage: Boolean get() = true

    // iOS always supports QualityPrioritization.SPEED
    override val supportsSpeedQualityPrioritization: Boolean get() = true

    // Focal Length

    override val focalLength: Double?
        get() = if (isIOSAtLeast(major = 26)) device.nominalFocalLengthIn35mmFilm.toDouble() else null

    override val lensAperture: Double get() = device.lensAperture.toDouble()

    // Continuity

    override val isContinuityCamera: Boolean
        get() = isIOSAtLeast(major = 16) && device.continuityCamera

    override val companionDeskViewCamera: CameraDevice?
        get() = if (isIOSAtLeast(major = 16)) device.companionDeskViewCamera?.let { IosCameraDevice(it) } else null

    override val mediaTypes: List<MediaType>
        get() = listOf(MediaType.VIDEO, MediaType.DEPTH, MediaType.METADATA)
            .filter { device.hasMediaType(it.toAVMediaType()) }

    // Focus

    override val supportsFocusMetering: Boolean
        get() = device.isFocusModeSupported(AVCaptureFocusModeAutoFocus) ||
            device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)

    override val supportsFocusLocking: Boolean get() = device.lockingFocusWithCustomLensPositionSupported

    override val supportsSmoothAutoFocus: Boolean get() = device.smoothAutoFocusSupported

    // Exposure

    override val supportsExposureMetering: Boolean
        get() = device.isExposureModeSupported(AVCaptureExposureModeAutoExpose) ||
            device.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)

    override val supportsExposureLocking: Boolean
        get() = device.isExposureModeSupported(AVCaptureExposureModeCustom)

    override val supportsExposureBias: Boolean get() = true
    override val minExposureBias: Double get() = device.minExposureTargetBias.toDouble()
    override val maxExposureBias: Double get() = device.maxExposureTargetBias.toDouble()

    // White Balance

    override val supportsWhiteBalanceMetering: Boolean
        get() = device.isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeAutoWhiteBalance) ||
            device.isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance)

    override val maxWhiteBalanceGain: Double get() = device.maxWhiteBalanceGain.toDouble()

    override val supportsWhiteBalanceLocking: Boolean get() = device.lockingWhiteBalanceWithCustomDeviceGainsSupported

    // Flash

    override val hasFlash: Boolean get() = device.hasFlash
    override val hasTorch: Boolean get() = device.hasTorch

    // On iOS any device with a torch also supports custom torch level/strength
    override val supportsTorchStrength: Boolean get() = device.hasTorch
    override val minTorchStrength: Double get() = device.minTorchStrength
    override val maxTorchStrength: Double get() = device.maxTorchStrength

    // Low Light Boost

    override val supportsLowLightBoost: Boolean get() = device.lowLightBoostSupported

    // Zoom

    override val minZoom: Double get() = device.minAvailableVideoZoomFactor
    override val maxZoom: Double get() = device.maxAvailableVideoZoomFactor

    override val zoomLensSwitchFactors: List<Double>
        get() = device.virtualDeviceSwitchOverVideoZoomFactors.filterIsInstance<NSNumber>().map { it.doubleValue }

    override val supportsDistortionCorrection: Boolean get() = device.geometricDistortionCorrectionSupported

    override fun isSessionConfigSupported(config: CameraSessionConfig): Boolean {
        // Invalid type
        if (config !is IosCameraSessionConfig) return false
        val format = config.negotiatedFormat.format
        val depthFormat = config.negotiatedFormat.depthFormat
        val targetFPS = config.enabledConstraints.selectedFPS
        // The format must be from this `AVCaptureDevice`, the depth format from this format, and the FPS supported.
        return device.deviceFormats.contains(format) &&
            (depthFormat == null || format.depthDataFormats.contains(depthFormat)) &&
            (targetFPS == null || format.frameRateRanges.any { it.contains(targetFPS) })
    }

    override fun equals(other: Any?): Boolean = other is IosCameraDevice && other.device == device

    override fun hashCode(): Int = device.uniqueID.hashCode()

    override fun toString(): String = "CameraDevice($id, $localizedName)"
}
