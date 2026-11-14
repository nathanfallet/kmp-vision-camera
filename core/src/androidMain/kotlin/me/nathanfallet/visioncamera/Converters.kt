package me.nathanfallet.visioncamera

import androidx.camera.core.TorchState

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
