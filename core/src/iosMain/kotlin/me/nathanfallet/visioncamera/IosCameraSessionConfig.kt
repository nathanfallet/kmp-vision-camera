package me.nathanfallet.visioncamera

/**
 * The iOS [CameraSessionConfig], backed by a [NegotiatedFormat] and its [EnabledConstraints] (port of V5's
 * `HybridCameraSessionConfig.swift`).
 */
internal class IosCameraSessionConfig(
    val negotiatedFormat: NegotiatedFormat,
    val enabledConstraints: EnabledConstraints,
) : CameraSessionConfig {
    override val selectedFPS: Double?
        get() = enabledConstraints.selectedFPS

    override val selectedVideoStabilizationMode: TargetStabilizationMode?
        get() = enabledConstraints.selectedVideoStabilizationMode

    override val selectedPreviewStabilizationMode: TargetStabilizationMode?
        get() = enabledConstraints.selectedPreviewStabilizationMode

    override val selectedVideoDynamicRange: TargetDynamicRange?
        get() = enabledConstraints.selectedVideoDynamicRange

    // Highest Photo Quality implies Smart HDR, this is not directly controllable.
    override val isPhotoHDREnabled: Boolean
        get() = negotiatedFormat.format.highestPhotoQualitySupported

    override val nativePixelFormat: PixelFormat
        get() = negotiatedFormat.format.pixelFormat

    override val autoFocusSystem: AutoFocusSystem
        get() = autoFocusSystemOf(negotiatedFormat.format.autoFocusSystem)

    override val isBinned: Boolean
        get() = negotiatedFormat.format.videoBinned

    override fun toString(): String = listOf(
        "format: ${negotiatedFormat.format}",
        "selectedFPS: $selectedFPS",
        "selectedVideoStabilizationMode: $selectedVideoStabilizationMode",
        "selectedPreviewStabilizationMode: $selectedPreviewStabilizationMode",
        "selectedVideoDynamicRange: $selectedVideoDynamicRange",
        "isPhotoHDREnabled: $isPhotoHDREnabled",
    ).joinToString(prefix = "CameraSessionConfig(", postfix = ")")
}
