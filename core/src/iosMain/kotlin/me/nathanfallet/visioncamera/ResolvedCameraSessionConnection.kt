package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoPreviewLayer

/**
 * A [CameraSessionConnection] whose input and outputs have been resolved to their iOS implementations (port of V5's
 * `ResolvedCameraSessionConnection.swift`).
 */
internal class ResolvedCameraSessionConnection(connection: CameraSessionConnection) {
    val input: IosCameraDevice = resolveInput(connection.input)
    val outputs: List<OutputConfiguration> = connection.outputs.map { resolveOutput(it) }
    val initialExposureBias: Double? = connection.initialExposureBias
    val initialZoom: Double? = connection.initialZoom
    val onSessionConfigSelected: ((CameraSessionConfig) -> Unit)? = connection.onSessionConfigSelected
    val constraints: List<Constraint> = connection.constraints

    /** Whether any of the [outputs] requires an audio input. */
    val requiresAudioInput: Boolean
        get() = outputs.any { it.output.requiresAudioInput }

    /** Whether the given session [input] is this connection's input device. */
    fun isConnectedTo(input: AVCaptureInput): Boolean = (input as? AVCaptureDeviceInput)?.device == this.input.device

    /** Whether the given session [output] is one of this connection's outputs. */
    fun isConnectedTo(output: AVCaptureOutput): Boolean = outputs.any {
        (it.output as? Output.Capture)?.output?.output == output
    }

    /** Whether the given [previewLayer] is one of this connection's outputs. */
    fun isConnectedTo(previewLayer: AVCaptureVideoPreviewLayer): Boolean = outputs.any {
        (it.output as? Output.Preview)?.preview?.previewLayer == previewLayer
    }

    /** Whether the given session [connection] is part of this connection. */
    fun contains(connection: AVCaptureConnection): Boolean {
        val input = connection.deviceInput ?: return false
        val output = connection.output
        val previewLayer = connection.videoPreviewLayer
        return when {
            output != null -> isConnectedTo(input) && isConnectedTo(output)
            previewLayer != null -> isConnectedTo(input) && isConnectedTo(previewLayer)
            else -> false
        }
    }

    /** An output of this connection, with its configuration. */
    data class OutputConfiguration(
        val output: Output,
        val mirrorMode: MirrorMode,
    )

    /** A resolved output: either a regular [AVCaptureOutput], or a preview layer. */
    sealed interface Output {
        /** The common [CameraOutput] of this output. */
        val cameraOutput: CameraOutput

        /** Whether this output requires an audio input. */
        val requiresAudioInput: Boolean

        /** The [MediaType] of this output. */
        val mediaType: MediaType get() = cameraOutput.mediaType

        /** A regular [AVCaptureOutput] output. */
        data class Capture(val output: NativeCameraOutput) : Output {
            override val cameraOutput: CameraOutput get() = output
            override val requiresAudioInput: Boolean get() = output.requiresAudioInput
        }

        /** A preview layer output. */
        data class Preview(val preview: NativePreviewViewOutput) : Output {
            override val cameraOutput: CameraOutput get() = preview
            override val requiresAudioInput: Boolean get() = false
        }
    }

    private companion object {
        fun resolveInput(input: CameraDeviceOrPosition): IosCameraDevice = when (input) {
            is IosCameraDevice -> input
            is CameraDevice ->
                throw VisionCameraException("The given CameraDevice \"$input\" is not an iOS CameraDevice!")
            is TargetCameraPosition -> IosCameraDevice(
                defaultCaptureDevice(input)
                    ?: throw VisionCameraException("No CameraDevice exists at position \"$input\"!"),
            )
        }

        fun resolveOutput(configuration: CameraOutputConfiguration): OutputConfiguration {
            val output = when (val output = configuration.output) {
                is NativeCameraOutput -> Output.Capture(output)
                is NativePreviewViewOutput -> Output.Preview(output)
                else -> throw VisionCameraException(
                    "Output \"$output\" is not of type `NativeCameraOutput` or `NativePreviewViewOutput`!",
                )
            }
            return OutputConfiguration(output = output, mirrorMode = configuration.mirrorMode)
        }
    }
}
