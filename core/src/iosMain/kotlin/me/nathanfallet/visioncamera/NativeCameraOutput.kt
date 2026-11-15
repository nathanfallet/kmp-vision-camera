package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoPreviewLayer

// Port of V5's Public/ResolutionNegotiationParticipant.swift, Public/NativeCameraOutput.swift,
// Public/NativePreviewViewOutput.swift and Public/OutputConfig.swift.

/**
 * The kind of stream a [ResolutionNegotiationParticipant] negotiates a resolution for.
 *
 * This is an extension point for plugin modules, see [NativeCameraOutput].
 */
enum class StreamType {
    /** A video stream (video recording, frame processing, ...). */
    VIDEO,

    /** A photo stream. */
    PHOTO,

    /** A depth video stream. */
    DEPTH_VIDEO,

    /** A depth photo stream. */
    DEPTH_PHOTO,
}

/**
 * The resolution an output would like to stream at.
 *
 * This is an extension point for plugin modules, see [NativeCameraOutput].
 */
sealed interface ResolutionRule {
    /** A resolution as close as possible to [size]. */
    data class ClosestTo(
        /** The target resolution. */
        val size: Size,
    ) : ResolutionRule

    /** Any resolution covering at least [size]. */
    data class Min(
        /** The minimum resolution. */
        val size: Size,
    ) : ResolutionRule

    /** Any resolution. */
    data object Any : ResolutionRule
}

/**
 * Represents a Camera Output that participates in resolution negotiations.
 *
 * This is required for using a [ResolutionBiasConstraint] for the given Camera Output, so the Camera pipeline can
 * internally figure out a most suitable resolution for all outputs.
 *
 * This is an extension point for plugin modules, see [NativeCameraOutput].
 */
interface ResolutionNegotiationParticipant {
    /** The target resolution of this output, negotiated with other outputs and [ResolutionBiasConstraint]s. */
    val targetResolution: ResolutionRule

    /** The stream type the [targetResolution] specifies. */
    val streamType: StreamType
}

/**
 * The configuration applied to an output when the [CameraSession] is configured.
 *
 * This is an extension point for plugin modules, passed to [NativeCameraOutput.configure].
 */
data class OutputConfiguration(
    /** The [MirrorMode] the output should apply. */
    val mirrorMode: MirrorMode,
)

/**
 * A [CameraOutput] backed by an [AVCaptureOutput] (photo, video, frames...), that is added to the
 * `AVCaptureSession` and manually connected to its input in [CameraSession.configure].
 *
 * This is an extension point for plugin modules: a module (e.g. `barcode`) adds its own output to a [CameraSession]
 * by implementing [NativeCameraOutput], and the session connects its [output] like the built-in outputs'.
 */
interface NativeCameraOutput : CameraOutput, ResolutionNegotiationParticipant {
    /** The actual [AVCaptureOutput] that should be added to the `AVCaptureSession`. */
    val output: AVCaptureOutput

    /**
     * Whether this output requires an audio input attached to its [output]. If `true`, both the camera input and an
     * audio input will be connected to the [output].
     */
    val requiresAudioInput: Boolean

    /** Whether this output requires a depth format to be enabled via `AVCaptureDevice.activeDepthDataFormat`. */
    val requiresDepthFormat: Boolean

    /**
     * Called whenever the [OutputConfiguration] might change, in a `beginConfiguration()`/`commitConfiguration()`
     * batch. The output is expected to apply all configs such as orientation or mirroring settings in here.
     */
    fun configure(config: OutputConfiguration)
}

/**
 * A [CameraOutput] backed by an [AVCaptureVideoPreviewLayer], displayed by a [PreviewView].
 */
internal interface NativePreviewViewOutput : CameraOutput, ResolutionNegotiationParticipant {
    /** The [AVCaptureVideoPreviewLayer] displaying the Camera feed. */
    val previewLayer: AVCaptureVideoPreviewLayer

    /** Applies the given [config] (e.g. mirroring) to the preview connection. */
    fun configure(config: OutputConfiguration)
}
