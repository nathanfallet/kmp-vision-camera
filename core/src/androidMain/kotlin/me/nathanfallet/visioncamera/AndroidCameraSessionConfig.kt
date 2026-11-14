package me.nathanfallet.visioncamera

import androidx.camera.core.SessionConfig

/**
 * The Android [CameraSessionConfig], wrapping a CameraX [SessionConfig].
 */
internal class AndroidCameraSessionConfig(
    /** The CameraX [SessionConfig]. */
    val sessionConfig: SessionConfig,
    private val resolvedConfig: NativeCameraOutput.Config,
) : CameraSessionConfig {
    override val selectedFPS: Double? get() = resolvedConfig.fpsRange?.upper?.toDouble()
    override val selectedVideoStabilizationMode: TargetStabilizationMode? get() = resolvedConfig.videoStabilizationMode
    override val selectedPreviewStabilizationMode: TargetStabilizationMode?
        get() = resolvedConfig.previewStabilizationMode
    override val selectedVideoDynamicRange: TargetDynamicRange? get() = resolvedConfig.videoDynamicRange
    override val isPhotoHDREnabled: Boolean get() = resolvedConfig.photoHDR ?: false

    // CameraX streams in PRIVATE.
    override val nativePixelFormat: PixelFormat get() = PixelFormat.Private
    override val autoFocusSystem: AutoFocusSystem get() = AutoFocusSystem.PHASE_DETECTION

    // In CameraX, currently every stream is binned, see https://issuetracker.google.com/issues/500394299
    override val isBinned: Boolean get() = true

    override fun toString(): String =
        "CameraSessionConfig(selectedFPS: $selectedFPS, selectedVideoStabilizationMode: " +
            "$selectedVideoStabilizationMode, selectedPreviewStabilizationMode: $selectedPreviewStabilizationMode, " +
            "selectedVideoDynamicRange: $selectedVideoDynamicRange, isPhotoHDREnabled: $isPhotoHDREnabled)"
}
