package me.nathanfallet.visioncamera

import android.util.Log
import android.util.Range
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.video.Recorder
import kotlin.math.abs

/**
 * The result of resolving constraints: the CameraX [SessionConfig] to bind, the use-cases it contains, and the
 * resolved output configuration.
 */
internal data class ResolvedSessionConfig(
    val sessionConfig: SessionConfig,
    val preparedUseCases: List<NativeCameraOutput.PreparedUseCase>,
    val resolvedConfig: NativeCameraOutput.Config,
) {
    fun toCameraSessionConfig(): AndroidCameraSessionConfig = AndroidCameraSessionConfig(sessionConfig, resolvedConfig)
}

/**
 * Resolves [Constraint]s into a CameraX [SessionConfig] the camera supports.
 */
internal object ConstraintResolver {
    /**
     * Resolves the given [constraints] into a [ResolvedSessionConfig] that [cameraInfo] supports.
     *
     * Constraints are ordered by priority (highest first). First-of-type wins: if duplicate constraint types exist,
     * only the highest-priority one is used. The resolver works in two passes:
     * 1. **Feature pass**: progressively downgrades non-FPS constraints (stabilization, HDR, etc.) until
     *    `isSessionConfigSupported` accepts the use-case combination.
     * 2. **FPS pass**: queries the FPS ranges supported by the validated [SessionConfig] (they depend on the
     *    enabled features) and picks the best match for the FPS target.
     */
    fun resolveConstraints(
        cameraInfo: CameraInfo,
        outputConfigurations: List<CameraOutputConfiguration>,
        constraints: List<Constraint>,
    ): ResolvedSessionConfig {
        // Separate FPS from other constraints, FPS is resolved after features.
        val fpsConstraint = constraints.firstNotNullOfOrNull { it as? FPSConstraint }
        val activeConstraints = constraints
            .filter { it !is FPSConstraint }
            .filter { it.isSupportedIndividually(cameraInfo) }
            .toMutableList()

        // Pass 1: resolve features via isSessionConfigSupported, without FPS.
        while (true) {
            val config = activeConstraints.toConfig()
            val preparedUseCases = outputConfigurations.map { outputConfiguration ->
                val output = outputConfiguration.output as? NativeCameraOutput
                    ?: throw VisionCameraException(
                        "The given output (${outputConfiguration.output}) was not created by VisionCamera!",
                    )
                output.createUseCase(outputConfiguration.mirrorMode, config)
            }
            val sessionConfig = SessionConfig.Builder(preparedUseCases.map { it.useCase }).build()

            if (cameraInfo.isSessionConfigSupported(sessionConfig) || activeConstraints.isEmpty()) {
                // Pass 2: resolve FPS against the validated use-case combination.
                val resolvedFPSRange = fpsConstraint?.resolveFPSRange(cameraInfo, sessionConfig)
                val finalConfig = config.copy(fpsRange = resolvedFPSRange)
                val finalSessionConfig = if (resolvedFPSRange != null) {
                    SessionConfig.Builder(preparedUseCases.map { it.useCase })
                        .setFrameRateRange(resolvedFPSRange)
                        .build()
                } else {
                    sessionConfig
                }
                Log.i(TAG, "Resolved constraints to: $finalConfig")
                return ResolvedSessionConfig(finalSessionConfig, preparedUseCases, finalConfig)
            }

            // Downgrade the lowest-priority (last) constraint, or drop it.
            val lastIndex = activeConstraints.lastIndex
            val downgraded = activeConstraints[lastIndex].getNextBestOption()
            if (downgraded != null) {
                Log.i(TAG, "Downgrading ${activeConstraints[lastIndex]} to $downgraded")
                activeConstraints[lastIndex] = downgraded
            } else {
                Log.i(TAG, "Dropping ${activeConstraints[lastIndex]}")
                activeConstraints.removeAt(lastIndex)
            }
        }
    }

    /**
     * Picks the supported range whose upper bound is closest to the target FPS. On ties, prefers tighter ranges
     * (higher lower bound).
     */
    private fun FPSConstraint.resolveFPSRange(cameraInfo: CameraInfo, sessionConfig: SessionConfig): Range<Int>? {
        val targetFps = fps.toInt()
        val supportedRanges = cameraInfo.getSupportedFrameRateRanges(sessionConfig)
        if (supportedRanges.isEmpty()) return null
        return supportedRanges.minWith(
            compareBy<Range<Int>> { abs(it.upper - targetFps) }.thenByDescending { it.lower },
        )
    }
}

/**
 * Resolves all [Constraint]s into a [NativeCameraOutput.Config]. First-of-type wins. FPS is resolved separately.
 */
internal fun List<Constraint>.toConfig(): NativeCameraOutput.Config = NativeCameraOutput.Config(
    fpsRange = null,
    previewStabilizationMode = firstNotNullOfOrNull { it as? PreviewStabilizationModeConstraint }
        ?.previewStabilizationMode,
    videoStabilizationMode = firstNotNullOfOrNull { it as? VideoStabilizationModeConstraint }
        ?.videoStabilizationMode,
    videoDynamicRange = firstNotNullOfOrNull { it as? VideoDynamicRangeConstraint }?.videoDynamicRange,
    photoHDR = firstNotNullOfOrNull { it as? PhotoHDRConstraint }?.photoHDR,
)

/**
 * Whether this [Constraint] can possibly be honored on [cameraInfo]. When in doubt, returns `true`: a constraint
 * that wrongly returns `false` is silently dropped with no chance of recovery.
 */
private fun Constraint.isSupportedIndividually(cameraInfo: CameraInfo): Boolean = when (this) {
    is VideoStabilizationModeConstraint -> videoStabilizationMode.isSupportedOrAuto {
        Recorder.getVideoCapabilities(cameraInfo).isStabilizationSupported
    }
    is PreviewStabilizationModeConstraint -> previewStabilizationMode.isSupportedOrAuto {
        Preview.getPreviewCapabilities(cameraInfo).isStabilizationSupported
    }
    is VideoDynamicRangeConstraint -> Recorder.getVideoCapabilities(cameraInfo)
        .supportedDynamicRanges
        .contains(videoDynamicRange.toCameraXDynamicRange())
    is PhotoHDRConstraint -> !photoHDR || ImageCapture.getImageCaptureCapabilities(cameraInfo)
        .supportedOutputFormats
        .contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
    // FPS is resolved separately, resolution is handled by CameraX, pixel format by the downgrade loop, and
    // binning is not configurable in CameraX.
    is FPSConstraint, is ResolutionBiasConstraint, is PixelFormatConstraint, is BinnedConstraint -> true
}

private inline fun TargetStabilizationMode.isSupportedOrAuto(isStabilizationSupported: () -> Boolean): Boolean =
    when (this) {
        TargetStabilizationMode.Off, TargetStabilizationMode.Auto -> true
        else -> isStabilizationSupported()
    }

/** CameraX only knows on/off, so all specific modes downgrade to auto first, then off. */
private fun TargetStabilizationMode.getNextBestMode(): TargetStabilizationMode? = when (this) {
    TargetStabilizationMode.Off -> null
    TargetStabilizationMode.Auto -> TargetStabilizationMode.Off
    is StabilizationMode -> TargetStabilizationMode.Auto
}

private fun TargetDynamicRange.getNextBestDynamicRange(): TargetDynamicRange? = when {
    colorSpace == ColorSpace.HLG_BT2020 -> copy(colorSpace = ColorSpace.SRGB)
    colorSpace == ColorSpace.DOLBY_VISION -> copy(colorSpace = ColorSpace.HLG_BT2020)
    colorSpace == ColorSpace.APPLE_LOG || colorSpace == ColorSpace.APPLE_LOG_2 -> copy(colorSpace = ColorSpace.SRGB)
    bitDepth == DynamicRangeBitDepth.HDR_10_BIT ->
        TargetDynamicRange(DynamicRangeBitDepth.SDR_8_BIT, ColorSpace.SRGB, colorRange)
    else -> null
}

/**
 * The next-best downgraded version of this [Constraint], or `null` if it can not be lowered further (it should
 * then be dropped).
 */
private fun Constraint.getNextBestOption(): Constraint? = when (this) {
    is VideoStabilizationModeConstraint ->
        videoStabilizationMode.getNextBestMode()?.let { VideoStabilizationModeConstraint(it) }
    is PreviewStabilizationModeConstraint ->
        previewStabilizationMode.getNextBestMode()?.let { PreviewStabilizationModeConstraint(it) }
    is VideoDynamicRangeConstraint -> videoDynamicRange.getNextBestDynamicRange()?.let { VideoDynamicRangeConstraint(it) }
    // FPS is never in the downgrade loop, Photo HDR is on or off, and the others have no fallback.
    is FPSConstraint, is ResolutionBiasConstraint, is PhotoHDRConstraint, is PixelFormatConstraint,
    is BinnedConstraint,
    -> null
}
