package me.nathanfallet.visioncamera

import android.os.Build
import androidx.camera.core.CameraInfo

/**
 * A physical [CameraDevice] that is part of a logical [AndroidCameraDevice].
 *
 * CameraX exposes very little about physical cameras (see https://issuetracker.google.com/issues/496096527), so
 * most capabilities are unavailable, like in VisionCamera.
 */
@Suppress("TooManyFunctions")
internal class AndroidPhysicalCameraDevice(
    override val cameraInfo: CameraInfo,
) : NativeCameraDevice {
    private val cameraCharacteristics = cameraInfo.cameraCharacteristicsOrNull

    override val id: String get() = cameraInfo.cameraIdOrNull.orEmpty()
    override val modelID: String get() = "physical:$position:$id"
    override val localizedName: String get() = cameraInfo.localizedName(isPhysical = true)
    override val manufacturer: String get() = Build.MANUFACTURER
    override val type: DeviceType get() = cameraInfo.deviceType(isPhysical = true)
    override val position: CameraPosition get() = cameraInfo.position
    override val physicalDevices: List<CameraDevice> = emptyList()
    override val isVirtualDevice: Boolean = false
    override val supportedPixelFormats: List<PixelFormat> = emptyList()
    override val supportsPhotoHDR: Boolean = false
    override val supportedVideoDynamicRanges: List<DynamicRange> = emptyList()
    override val supportedFPSRanges: List<Range> = emptyList()
    override val supportsPreviewImage: Boolean = false
    override val supportsSpeedQualityPrioritization: Boolean = false
    override val focalLength: Double? get() = cameraCharacteristics?.focalLength?.toDouble()
    override val lensAperture: Double get() = cameraCharacteristics?.defaultAperture ?: 0.0
    override val isContinuityCamera: Boolean = false
    override val companionDeskViewCamera: CameraDevice? = null
    override val mediaTypes: List<MediaType> = emptyList()
    override val supportsFocusMetering: Boolean = false
    override val supportsFocusLocking: Boolean = false
    override val supportsSmoothAutoFocus: Boolean = false
    override val supportsExposureMetering: Boolean = false
    override val supportsExposureLocking: Boolean = false
    override val supportsExposureBias: Boolean = false
    override val minExposureBias: Double = 0.0
    override val maxExposureBias: Double = 0.0
    override val supportsWhiteBalanceMetering: Boolean = false
    override val maxWhiteBalanceGain: Double = 0.0
    override val supportsWhiteBalanceLocking: Boolean = false
    override val hasFlash: Boolean = false
    override val hasTorch: Boolean = false
    override val supportsTorchStrength: Boolean = false
    override val minTorchStrength: Double = 0.0
    override val maxTorchStrength: Double = 0.0
    override val supportsLowLightBoost: Boolean = false
    override val minZoom: Double = 0.0
    override val maxZoom: Double = 0.0
    override val zoomLensSwitchFactors: List<Double> = emptyList()
    override val supportsDistortionCorrection: Boolean = false

    override fun getSupportedResolutions(outputStreamType: OutputStreamType): List<Size> = emptyList()

    override fun supportsOutput(output: CameraOutput): Boolean = false

    override fun supportsFPS(fps: Double): Boolean = supportedFPSRanges.any { fps in it }

    override fun supportsVideoStabilizationMode(videoStabilizationMode: TargetStabilizationMode): Boolean = false

    override fun supportsPreviewStabilizationMode(previewStabilizationMode: TargetStabilizationMode): Boolean = false

    override fun isSessionConfigSupported(config: CameraSessionConfig): Boolean = false

    override fun toString(): String = "PhysicalCameraDevice($id)"
}
