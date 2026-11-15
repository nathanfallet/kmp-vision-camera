package me.nathanfallet.visioncamera

import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState

/** The number of milliseconds in a second. */
internal const val MILLISECONDS_PER_SECOND = 1_000.0

/** The number of nanoseconds in a second. */
internal const val NANOSECONDS_PER_SECOND = 1_000_000_000.0

/** Converts a CameraX dynamic range to a [DynamicRange]. */
internal fun androidx.camera.core.DynamicRange.toDynamicRange(): DynamicRange {
    val bitDepth = when (bitDepth) {
        androidx.camera.core.DynamicRange.BIT_DEPTH_8_BIT -> DynamicRangeBitDepth.SDR_8_BIT
        androidx.camera.core.DynamicRange.BIT_DEPTH_10_BIT -> DynamicRangeBitDepth.HDR_10_BIT
        else -> DynamicRangeBitDepth.UNKNOWN
    }
    val colorSpace = when (encoding) {
        androidx.camera.core.DynamicRange.ENCODING_SDR -> ColorSpace.SRGB
        androidx.camera.core.DynamicRange.ENCODING_HLG,
        androidx.camera.core.DynamicRange.ENCODING_HDR10,
        androidx.camera.core.DynamicRange.ENCODING_HDR10_PLUS,
        -> ColorSpace.HLG_BT2020
        androidx.camera.core.DynamicRange.ENCODING_DOLBY_VISION -> ColorSpace.DOLBY_VISION
        else -> ColorSpace.UNKNOWN
    }
    return DynamicRange(bitDepth, colorSpace, ColorRange.FULL)
}

/** Converts a [TargetDynamicRange] to a CameraX dynamic range. */
internal fun TargetDynamicRange.toCameraXDynamicRange(): androidx.camera.core.DynamicRange = when (bitDepth) {
    DynamicRangeBitDepth.HDR_10_BIT -> when (colorSpace) {
        ColorSpace.DOLBY_VISION -> androidx.camera.core.DynamicRange.DOLBY_VISION_10_BIT
        ColorSpace.HLG_BT2020 -> androidx.camera.core.DynamicRange.HLG_10_BIT
        else -> androidx.camera.core.DynamicRange.HDR_UNSPECIFIED_10_BIT
    }
    else -> when (colorSpace) {
        ColorSpace.DOLBY_VISION -> androidx.camera.core.DynamicRange.DOLBY_VISION_8_BIT
        else -> androidx.camera.core.DynamicRange.SDR
    }
}

/** Converts a [MirrorMode] to a CameraX mirror mode. */
internal fun MirrorMode.toCameraXMirrorMode(): Int = when (this) {
    MirrorMode.ON -> androidx.camera.core.MirrorMode.MIRROR_MODE_ON
    MirrorMode.OFF -> androidx.camera.core.MirrorMode.MIRROR_MODE_OFF
    MirrorMode.AUTO -> androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
}

/** Converts a CameraX mirror mode to a [MirrorMode]. */
internal fun mirrorModeFromCameraX(mirrorMode: Int): MirrorMode = when (mirrorMode) {
    androidx.camera.core.MirrorMode.MIRROR_MODE_ON -> MirrorMode.ON
    androidx.camera.core.MirrorMode.MIRROR_MODE_OFF -> MirrorMode.OFF
    else -> MirrorMode.AUTO
}

/** Converts a CameraX torch state to a [TorchMode]. */
internal fun torchModeFromTorchState(torchState: Int): TorchMode = when (torchState) {
    TorchState.ON -> TorchMode.ON
    else -> TorchMode.OFF
}

/** Converts a [FlashMode] to a CameraX `ImageCapture` flash mode. */
internal fun FlashMode.toCameraXFlashMode(): Int = when (this) {
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
}

/** Converts a [QualityPrioritization] to a CameraX `ImageCapture` capture mode. */
@OptIn(ExperimentalZeroShutterLag::class)
internal fun QualityPrioritization.toCameraXCaptureMode(): Int = when (this) {
    QualityPrioritization.SPEED -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
    QualityPrioritization.BALANCED -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    QualityPrioritization.QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
}

/** Converts a [MeteringMode] to a CameraX `FocusMeteringAction` flag. */
internal fun MeteringMode.toCameraXMeteringMode(): Int = when (this) {
    MeteringMode.AE -> FocusMeteringAction.FLAG_AE
    MeteringMode.AF -> FocusMeteringAction.FLAG_AF
    MeteringMode.AWB -> FocusMeteringAction.FLAG_AWB
}

/** Combines these [MeteringMode]s into CameraX `FocusMeteringAction` flags. */
internal fun List<MeteringMode>.toCameraXMeteringMode(): Int {
    if (isEmpty()) throw VisionCameraException("MeteringModes cannot be empty!")
    return fold(0) { acc, mode -> acc or mode.toCameraXMeteringMode() }
}

/** Converts a [Location] to an Android [android.location.Location]. */
internal fun Location.toAndroidLocation(): android.location.Location =
    android.location.Location("VisionCamera").also { location ->
        location.latitude = latitude
        location.longitude = longitude
        location.altitude = altitude
        location.accuracy = horizontalAccuracy.toFloat()
        location.verticalAccuracyMeters = verticalAccuracy.toFloat()
        location.time = timestamp.toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock = isMock
    }
