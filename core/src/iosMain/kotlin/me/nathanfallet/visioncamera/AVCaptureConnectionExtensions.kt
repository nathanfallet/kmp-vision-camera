package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureInputPort
import platform.AVFoundation.AVMediaTypeAudio
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions

// Port of V5's AVCaptureConnection+deviceInput.swift, AVCaptureConnection+init.swift,
// AVCaptureConnection+inputStreamResolution.swift, AVCaptureConnection+isConnectedTo.swift,
// AVCaptureConnection+isConnectedToAudioInput.swift, AVCaptureConnection+isConnectedToPreview.swift,
// AVCaptureConnection+mirrorMode.swift, AVCaptureConnection+orientation.swift and AVCaptureInput+isMicrophone.swift.

/** Whether this input is a microphone (audio) input. */
internal val AVCaptureInput.isMicrophone: Boolean
    get() = (this as? AVCaptureDeviceInput)?.device?.hasMediaType(AVMediaTypeAudio) == true

/** All input ports of this input. */
internal val AVCaptureInput.inputPorts: List<AVCaptureInputPort>
    get() = ports.filterIsInstance<AVCaptureInputPort>()

/** All input ports of this connection. */
internal val AVCaptureConnection.connectionInputPorts: List<AVCaptureInputPort>
    get() = inputPorts.filterIsInstance<AVCaptureInputPort>()

/**
 * The connection's current device input, or `null` if it has none, or if it is a connection to a metadata input.
 */
internal val AVCaptureConnection.deviceInput: AVCaptureDeviceInput?
    get() = connectionInputPorts.firstNotNullOfOrNull { it.input as? AVCaptureDeviceInput }

/** Whether this connection streams into a preview layer. */
internal val AVCaptureConnection.isConnectedToPreview: Boolean
    get() = videoPreviewLayer != null

/** Whether this connection streams from an audio input. */
internal val AVCaptureConnection.isConnectedToAudioInput: Boolean
    get() = connectionInputPorts.all { it.input.isMicrophone }

/** Whether this connection streams into the given resolved [output]. */
internal fun AVCaptureConnection.isConnectedTo(output: ResolvedCameraSessionConnection.Output): Boolean =
    when (output) {
        is ResolvedCameraSessionConnection.Output.Capture -> this.output == output.output.output
        is ResolvedCameraSessionConnection.Output.Preview -> videoPreviewLayer == output.preview.previewLayer
    }

/**
 * The resolution of the stream currently flowing through this connection's first input port, in sensor-native
 * (un-rotated) pixels, or `null` until the session is configured.
 *
 * This is an extension point for plugin modules, e.g. to implement a [NativeCameraOutput]'s
 * [CameraOutput.currentResolution] from its output's video connection.
 */
val AVCaptureConnection.inputStreamResolution: Size?
    get() {
        val formatDescription = connectionInputPorts.firstOrNull()?.formatDescription ?: return null
        return CMVideoFormatDescriptionGetDimensions(formatDescription).toSize()
    }

/** The current [CameraOrientation] of this connection. */
@Suppress("DEPRECATION")
internal val AVCaptureConnection.orientation: CameraOrientation
    get() = cameraOrientationOf(videoOrientation)

/**
 * Sets the [CameraOrientation] of this connection.
 *
 * @throws VisionCameraException If this connection does not support changing its orientation.
 */
@Suppress("DEPRECATION")
internal fun AVCaptureConnection.setOrientation(orientation: CameraOrientation) {
    if (!supportsVideoOrientation) {
        throw VisionCameraException(
            "Cannot set orientation=\"$orientation\" - this connection does not support orientation changing",
        )
    }
    videoOrientation = orientation.toAVCaptureVideoOrientation()
}

/**
 * Sets the [MirrorMode] of this connection.
 *
 * @throws VisionCameraException If [MirrorMode.ON] is requested but this connection does not support mirroring.
 */
internal fun AVCaptureConnection.setMirrorMode(mirrorMode: MirrorMode) {
    when (mirrorMode) {
        MirrorMode.AUTO -> automaticallyAdjustsVideoMirroring = true
        MirrorMode.ON -> {
            if (!supportsVideoMirroring) {
                throw VisionCameraException(
                    "Cannot set mirrorMode=\"$mirrorMode\" - this connection does not support video mirroring!",
                )
            }
            automaticallyAdjustsVideoMirroring = false
            videoMirrored = true
        }
        MirrorMode.OFF -> {
            // Mirroring is not supported, but setting it to `false` may still throw. So just ignore
            if (!supportsVideoMirroring) return
            automaticallyAdjustsVideoMirroring = false
            videoMirrored = false
        }
    }
}

/**
 * Creates a connection from the given [input] to the given resolved [output].
 *
 * @throws VisionCameraException If the [input] has no port for the [output]'s media type.
 */
internal fun createCaptureConnection(
    input: AVCaptureDeviceInput,
    output: ResolvedCameraSessionConnection.Output,
): AVCaptureConnection {
    val mediaType = output.mediaType.toAVMediaType()
    val targetPorts = input.inputPorts.filter { it.mediaType == mediaType }
    if (targetPorts.isEmpty()) {
        throw VisionCameraException(
            "Output \"$output\" operates on mediaType \"${output.mediaType}\", but Input " +
                "\"${input.device.uniqueID}\" does not have any ports for that mediaType!",
        )
    }
    return when (output) {
        // a) It's a normal AVCaptureOutput - connect it to all its desired ports
        is ResolvedCameraSessionConnection.Output.Capture ->
            AVCaptureConnection(inputPorts = targetPorts, output = output.output.output)
        // b) It's a preview AVCaptureVideoPreviewLayer
        is ResolvedCameraSessionConnection.Output.Preview ->
            AVCaptureConnection(inputPort = targetPorts.first(), videoPreviewLayer = output.preview.previewLayer)
    }
}
