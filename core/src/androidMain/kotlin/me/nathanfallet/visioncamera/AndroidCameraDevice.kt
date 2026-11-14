package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.video.Recorder

/**
 * A logical [CameraDevice] backed by a CameraX [CameraInfo].
 *
 * It uses public CameraX APIs as much as possible, and Camera2 APIs if necessary.
 */
@SuppressLint("RestrictedApi")
@Suppress("TooManyFunctions")
internal class AndroidCameraDevice(
    override val cameraInfo: CameraInfo,
) : NativeCameraDevice {
    private val cameraCharacteristics = cameraInfo.cameraCharacteristicsOrNull
    private val previewCapabilities by lazy { Preview.getPreviewCapabilities(cameraInfo) }
    private val imageCapabilities by lazy { ImageCapture.getImageCaptureCapabilities(cameraInfo) }
    private val videoCapabilities by lazy { Recorder.getVideoCapabilities(cameraInfo) }

    override val id: String get() = cameraInfo.cameraIdOrNull ?: cameraInfo.toString()
    override val modelID: String get() = cameraInfo.modelID
    override val localizedName: String get() = cameraInfo.localizedName(isPhysical = false)
    override val manufacturer: String get() = Build.MANUFACTURER
    override val type: DeviceType get() = cameraInfo.deviceType(isPhysical = false)
    override val position: CameraPosition get() = cameraInfo.position
    override val physicalDevices: List<CameraDevice> by lazy {
        cameraInfo.physicalCameraInfos.map { AndroidPhysicalCameraDevice(it) }
    }
    override val isVirtualDevice: Boolean get() = cameraInfo.isLogicalMultiCameraSupported

    override val supportedPixelFormats: List<PixelFormat>
        get() = cameraCharacteristics?.getPixelFormats().orEmpty()

    override fun getSupportedResolutions(outputStreamType: OutputStreamType): List<Size> =
        cameraCharacteristics?.getSupportedResolutions(outputStreamType).orEmpty()

    override fun supportsOutput(output: CameraOutput): Boolean = output is NativeCameraOutput

    override val supportsPhotoHDR: Boolean
        get() = imageCapabilities.supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
    override val supportedVideoDynamicRanges: List<DynamicRange>
        get() = videoCapabilities.supportedDynamicRanges.map { it.toDynamicRange() }
    override val supportedFPSRanges: List<Range>
        get() = cameraInfo.supportedFrameRateRanges.map { it.toRange() }

    override fun supportsFPS(fps: Double): Boolean = supportedFPSRanges.any { fps in it }

    override fun supportsVideoStabilizationMode(videoStabilizationMode: TargetStabilizationMode): Boolean =
        videoStabilizationMode.isSupported(videoCapabilities.isStabilizationSupported)

    override fun supportsPreviewStabilizationMode(previewStabilizationMode: TargetStabilizationMode): Boolean =
        previewStabilizationMode.isSupported(previewCapabilities.isStabilizationSupported)

    override val supportsPreviewImage: Boolean get() = imageCapabilities.isPostviewSupported
    override val supportsSpeedQualityPrioritization: Boolean
        @OptIn(ExperimentalZeroShutterLag::class)
        get() = cameraInfo.isZslSupported

    override val focalLength: Double? get() = cameraCharacteristics?.focalLength?.toDouble()
    override val lensAperture: Double get() = cameraCharacteristics?.defaultAperture ?: 0.0

    override val isContinuityCamera: Boolean get() = false
    override val companionDeskViewCamera: CameraDevice? get() = null
    override val mediaTypes: List<MediaType> get() = cameraCharacteristics.getMediaTypes()

    override val supportsFocusMetering: Boolean get() = supportsMetering(FocusMeteringAction.FLAG_AF)

    // CameraX doesn't support manual Focus/Exposure/WB locking yet.
    override val supportsFocusLocking: Boolean get() = false
    override val supportsSmoothAutoFocus: Boolean get() = false

    override val supportsExposureMetering: Boolean get() = supportsMetering(FocusMeteringAction.FLAG_AE)
    override val supportsExposureLocking: Boolean get() = false
    override val supportsExposureBias: Boolean get() = cameraInfo.exposureState.isExposureCompensationSupported
    override val minExposureBias: Double get() = cameraInfo.exposureState.exposureCompensationRange.lower.toDouble()
    override val maxExposureBias: Double get() = cameraInfo.exposureState.exposureCompensationRange.upper.toDouble()

    override val supportsWhiteBalanceMetering: Boolean get() = supportsMetering(FocusMeteringAction.FLAG_AWB)
    override val maxWhiteBalanceGain: Double get() = 0.0
    override val supportsWhiteBalanceLocking: Boolean get() = false

    override val hasFlash: Boolean get() = cameraInfo.hasFlashUnit()
    override val hasTorch: Boolean get() = cameraInfo.hasFlashUnit()
    override val supportsTorchStrength: Boolean get() = cameraInfo.isTorchStrengthSupported

    // Torch Strength ranges [1, maxTorchStrengthLevel]
    override val minTorchStrength: Double get() = if (supportsTorchStrength) 1.0 else 0.0
    override val maxTorchStrength: Double
        get() = if (supportsTorchStrength) cameraInfo.maxTorchStrengthLevel.toDouble() else 0.0

    override val supportsLowLightBoost: Boolean get() = cameraInfo.isLowLightBoostSupported

    override val minZoom: Double get() = cameraInfo.zoomState.value?.minZoomRatio?.toDouble() ?: 0.0
    override val maxZoom: Double get() = cameraInfo.zoomState.value?.maxZoomRatio?.toDouble() ?: 0.0

    // Physical cameras throw "Physical camera doesn't support this function" when accessing their zoom state,
    // so V5 doesn't expose switch-over factors on Android yet.
    override val zoomLensSwitchFactors: List<Double> get() = emptyList()

    override val supportsDistortionCorrection: Boolean
        get() = cameraCharacteristics?.supportsDistortionCorrection ?: false

    override fun isSessionConfigSupported(config: CameraSessionConfig): Boolean {
        val nativeConfig = config as? AndroidCameraSessionConfig
            ?: throw VisionCameraException("`config` was not created by VisionCamera!")
        return cameraInfo.isSessionConfigSupported(nativeConfig.sessionConfig)
    }

    private fun supportsMetering(meteringMode: Int): Boolean =
        cameraInfo.isFocusMeteringSupported(FocusMeteringAction.Builder(dummyCenterPoint, meteringMode).build())

    override fun equals(other: Any?): Boolean = other is AndroidCameraDevice && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CameraDevice($id, $localizedName)"

    private companion object {
        val dummyCenterPoint: MeteringPoint by lazy {
            SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        }
    }
}

/**
 * Whether this mode is supported, given whether CameraX supports stabilization at all: CameraX only knows "on" and
 * "off", so only [StabilizationMode.STANDARD] maps to a real mode.
 */
internal fun TargetStabilizationMode.isSupported(isStabilizationSupported: Boolean): Boolean = when (this) {
    TargetStabilizationMode.Off, TargetStabilizationMode.Auto -> true
    StabilizationMode.STANDARD -> isStabilizationSupported
    else -> false
}
