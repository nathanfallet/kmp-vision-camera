package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.cameraCharacteristics
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider

/** The Camera2 id of this camera, or `null` if it is not a Camera2 device. */
internal val CameraInfo.cameraIdOrNull: String?
    get() = try {
        cameraId
    } catch (_: IllegalArgumentException) {
        Log.w(TAG, "Camera Device $this is not a Camera2 device!")
        null
    }

/** The Camera2 characteristics of this camera, or `null` if it is not a Camera2 device. */
internal val CameraInfo.cameraCharacteristicsOrNull: CameraCharacteristics?
    get() = try {
        cameraCharacteristics
    } catch (_: IllegalArgumentException) {
        Log.w(TAG, "Camera Device $this is not a Camera2 device!")
        null
    }

/** The [CameraPosition] of this camera. */
internal val CameraInfo.position: CameraPosition
    @OptIn(ExperimentalLensFacing::class)
    get() = when (lensFacing) {
        CameraSelector.LENS_FACING_BACK -> CameraPosition.BACK
        CameraSelector.LENS_FACING_FRONT -> CameraPosition.FRONT
        CameraSelector.LENS_FACING_EXTERNAL -> CameraPosition.EXTERNAL
        CameraSelector.LENS_FACING_UNKNOWN -> CameraPosition.UNSPECIFIED
        else -> {
            Log.w(TAG, "Received unknown LENS_FACING: $lensFacing")
            CameraPosition.UNSPECIFIED
        }
    }

/** A model identifier for this camera. */
internal val CameraInfo.modelID: String
    @SuppressLint("RestrictedApi")
    get() = "$implementationType:${position.name}"

/** A user friendly name for this camera, e.g. "Back Triple Camera". */
internal fun CameraInfo.localizedName(isPhysical: Boolean): String {
    val positionString = when (position) {
        CameraPosition.EXTERNAL -> "External"
        CameraPosition.FRONT -> "Front"
        CameraPosition.BACK -> "Back"
        CameraPosition.UNSPECIFIED -> "Unknown"
    }
    val count = if (isPhysical) "Physical " else multiCameraName(physicalCameraInfos.size)?.let { "$it " }.orEmpty()
    return "$positionString ${count}Camera"
}

@Suppress("MagicNumber")
private fun multiCameraName(physicalCameraCount: Int): String? = when (physicalCameraCount) {
    2 -> "Dual"
    3 -> "Triple"
    4 -> "Quad"
    else -> null
}

/** The [DeviceType] of this camera. */
internal fun CameraInfo.deviceType(isPhysical: Boolean): DeviceType = when {
    // Physical camera infos throw when accessing `intrinsicZoomRatio`.
    isPhysical -> DeviceType.UNKNOWN
    physicalCameraInfos.size > 1 -> when (multiCameraName(physicalCameraInfos.size)) {
        "Dual" -> DeviceType.DUAL
        "Triple" -> DeviceType.TRIPLE
        "Quad" -> DeviceType.QUAD
        else -> DeviceType.UNKNOWN
    }
    // If it supports only DEPTH, it's a ToF Depth Camera
    cameraCharacteristicsOrNull?.supportsDepthOnly == true -> DeviceType.TIME_OF_FLIGHT_DEPTH
    intrinsicZoomRatio < 1f -> DeviceType.ULTRA_WIDE_ANGLE
    intrinsicZoomRatio > 1f -> DeviceType.TELEPHOTO
    else -> DeviceType.WIDE_ANGLE
}

private val CameraCharacteristics.outputFormats: IntArray
    get() = this[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.outputFormats ?: intArrayOf()

private val CameraCharacteristics.supportsDepthOnly: Boolean
    get() = outputFormats.isNotEmpty() && outputFormats.all { ImageFormatUtils.isDepthFormat(it) }

/** The nominal focal length of this camera. */
internal val CameraCharacteristics.focalLength: Float?
    get() = this[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull()

/** The default aperture of this camera. */
internal val CameraCharacteristics.defaultAperture: Double?
    get() = this[CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES]?.firstOrNull()?.toDouble()

/** Whether this camera supports distortion correction. */
internal val CameraCharacteristics.supportsDistortionCorrection: Boolean
    get() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val modes = this[CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES] ?: return false
        return modes.contains(CameraCharacteristics.DISTORTION_CORRECTION_MODE_FAST) ||
            modes.contains(CameraCharacteristics.DISTORTION_CORRECTION_MODE_HIGH_QUALITY)
    }

/** All [PixelFormat]s this camera can stream in. */
internal fun CameraCharacteristics.getPixelFormats(): List<PixelFormat> =
    outputFormats.map { pixelFormatFromImageFormat(it, PixelRange.UNKNOWN) }.distinct()

/** All [MediaType]s this camera can capture. */
internal fun CameraCharacteristics?.getMediaTypes(): List<MediaType> {
    if (this == null || this[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] == null) {
        // Just assume VIDEO works
        return listOf(MediaType.VIDEO)
    }
    return buildList {
        if (outputFormats.any { ImageFormatUtils.isVideoFormat(it) }) add(MediaType.VIDEO)
        if (outputFormats.any { ImageFormatUtils.isDepthFormat(it) }) add(MediaType.DEPTH)
    }
}

/** All resolutions this camera can produce for the given [outputStreamType]. */
internal fun CameraCharacteristics.getSupportedResolutions(outputStreamType: OutputStreamType): List<Size> {
    val streams = this[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] ?: return emptyList()
    fun sizesOf(filter: (Int) -> Boolean) =
        streams.outputFormats.filter(filter).flatMap { streams.getOutputSizes(it).orEmpty().toList() }
    val sizes = when (outputStreamType) {
        OutputStreamType.PHOTO -> {
            val photoFormats = streams.outputFormats.filter { ImageFormatUtils.isPhotoFormat(it) }
            sizesOf { ImageFormatUtils.isPhotoFormat(it) } +
                photoFormats.flatMap { streams.getHighResolutionOutputSizes(it).orEmpty().toList() }
        }
        OutputStreamType.VIDEO -> sizesOf { ImageFormatUtils.isVideoFormat(it) }
        OutputStreamType.STREAM -> streams.getOutputSizes(ImageReader::class.java).orEmpty().toList()
        OutputStreamType.DEPTH_PHOTO, OutputStreamType.DEPTH_STREAM -> sizesOf { ImageFormatUtils.isDepthFormat(it) }
    }
    return sizes.distinct().map { it.toSize() }
}

/**
 * Converts an Android [android.util.Size] to a [Size].
 *
 * This is an extension point for plugin modules, e.g. to implement a [NativeCameraOutput]'s
 * [CameraOutput.currentResolution] from its use case's `resolutionInfo`.
 */
fun android.util.Size.toSize(): Size = Size(width, height)

/** Converts an Android [android.util.Range] to a [Range]. */
internal fun android.util.Range<Int>.toRange(): Range = Range(lower.toDouble(), upper.toDouble())

/** Gets the platform's default camera at the given [position]. */
@OptIn(ExperimentalLensFacing::class)
internal fun ProcessCameraProvider.getDefaultCamera(position: TargetCameraPosition): CameraInfo {
    val selector = when (position) {
        TargetCameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        TargetCameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        TargetCameraPosition.EXTERNAL ->
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build()
    }
    return getCameraInfo(selector)
}

/** Gets the [CameraInfo] of the given connection input. */
internal fun ProcessCameraProvider.getCameraInfo(input: CameraDeviceOrPosition): CameraInfo = when (input) {
    is TargetCameraPosition -> getDefaultCamera(input)
    is NativeCameraDevice -> input.cameraInfo
    is CameraDevice -> throw VisionCameraException("CameraDevice $input was not created by VisionCamera!")
}
