package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVMediaTypeAudio
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

// Port of V5's AVCaptureSession+*.swift extensions.

/** All inputs of this session. */
internal val AVCaptureSession.sessionInputs: List<AVCaptureInput>
    get() = inputs.filterIsInstance<AVCaptureInput>()

/** All outputs of this session. */
internal val AVCaptureSession.sessionOutputs: List<AVCaptureOutput>
    get() = outputs.filterIsInstance<AVCaptureOutput>()

/** All connections of this session. */
internal val AVCaptureSession.sessionConnections: List<AVCaptureConnection>
    get() = connections.filterIsInstance<AVCaptureConnection>()

/**
 * Finds the [AVCaptureDeviceInput] of the given [device] in this session.
 *
 * @throws VisionCameraException If the [device] is not attached to this session yet.
 */
internal fun AVCaptureSession.findDeviceInput(device: AVCaptureDevice): AVCaptureDeviceInput =
    sessionInputs.filterIsInstance<AVCaptureDeviceInput>().firstOrNull { it.device == device }
        ?: throw VisionCameraException(
            "The given device \"${device.uniqueID}\" is not yet attached to the `CameraSession` - " +
                "cannot form a connection yet!",
        )

/** Whether this session already contains the given resolved [output]. */
internal fun AVCaptureSession.containsOutput(output: ResolvedCameraSessionConnection.Output): Boolean = when (output) {
    is ResolvedCameraSessionConnection.Output.Capture -> sessionOutputs.contains(output.output.output)
    is ResolvedCameraSessionConnection.Output.Preview -> output.preview.previewLayer.session == this
}

/**
 * Adds the given resolved [output] to this session, without forming any connections.
 *
 * @throws VisionCameraException If the output is already connected to a different session, or cannot be added.
 */
@Suppress("ThrowsCount")
internal fun AVCaptureSession.addOutputWithNoConnections(output: ResolvedCameraSessionConnection.Output) {
    when (output) {
        is ResolvedCameraSessionConnection.Output.Capture -> {
            // It's a normal AVCaptureOutput
            val captureOutput = output.output.output
            if (captureOutput.connections.isNotEmpty()) {
                throw VisionCameraException(
                    "The given Output \"$captureOutput\" is already connected to a different Camera Session!",
                )
            }
            if (!canAddOutput(captureOutput)) {
                throw VisionCameraException("Output \"$captureOutput\" cannot be added to Camera Session!")
            }
            Logger.info("Adding Output $captureOutput...")
            addOutputWithNoConnections(captureOutput)
        }
        is ResolvedCameraSessionConnection.Output.Preview -> {
            // It's an AVCaptureVideoPreviewLayer - we need to set its .session
            val previewLayer = output.preview.previewLayer
            if (previewLayer.session != null) {
                throw VisionCameraException(
                    "The given Preview Output \"$previewLayer\" is already connected to a different Camera Session!",
                )
            }
            Logger.info("Adding Preview $previewLayer...")
            previewLayer.setSessionWithNoConnection(this)
        }
    }
}

/** Whether this session already contains a connection from the given [input] device to the given [output]. */
internal fun AVCaptureSession.containsConnection(
    input: AVCaptureDevice,
    output: ResolvedCameraSessionConnection.Output,
): Boolean = sessionConnections.any { it.deviceInput?.device == input && it.isConnectedTo(output) }

/**
 * Adds a connection from the given [input] device to the given [output].
 *
 * @throws VisionCameraException If the connection cannot be added.
 */
internal fun AVCaptureSession.addConnection(input: AVCaptureDevice, output: ResolvedCameraSessionConnection.Output) {
    // 1. Get the `AVCaptureDeviceInput` for our `device`
    val deviceInput = findDeviceInput(input)
    // 2. Create the `AVCaptureConnection` (either output or preview)
    val connection = createCaptureConnection(deviceInput, output)
    // 3. Add it!
    if (!canAddConnection(connection)) {
        throw VisionCameraException("Connection \"$connection\" cannot be added to Camera Session!")
    }
    Logger.info("Adding Connection $connection...")
    addConnection(connection)
}

/**
 * Adds the default microphone as an input to this session, without forming any connections.
 *
 * @throws VisionCameraException If this device does not have a microphone.
 */
internal fun AVCaptureSession.addAudioInput() {
    val microphone = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
        ?: throw VisionCameraException("This device does not have a microphone!")
    val input = createDeviceInput(microphone)
    Logger.info("Adding microphone $input...")
    addInputWithNoConnections(input)
}

/**
 * Connects the audio input of this session to the given [output].
 *
 * @throws VisionCameraException If no audio input is attached, or if the connection cannot be added.
 */
@Suppress("ThrowsCount")
internal fun AVCaptureSession.addAudioConnection(output: AVCaptureOutput) {
    val audioDevice = sessionInputs.firstOrNull { it.isMicrophone }
        ?: throw VisionCameraException(
            "Tried to form a Connection between the Audio Device and Output $output, but no Audio Device is " +
                "attached to this CameraSession!",
        )
    val audioPort = audioDevice.inputPorts.firstOrNull { it.mediaType == AVMediaTypeAudio }
        ?: throw VisionCameraException("Audio Device does not have an audio port!")
    val connection = AVCaptureConnection(inputPorts = listOf(audioPort), output = output)
    if (!canAddConnection(connection)) {
        throw VisionCameraException("Audio Connection \"$connection\" cannot be added to Camera Session!")
    }
    addConnection(connection)
}

/** Whether this session already contains an audio connection to the given [output]. */
internal fun AVCaptureSession.containsAudioConnection(output: AVCaptureOutput): Boolean =
    sessionConnections.any { it.isConnectedToAudioInput && it.output == output }

/**
 * Creates an [AVCaptureDeviceInput] for the given [device].
 *
 * @throws VisionCameraException If the input could not be created (e.g. the device is in use).
 */
internal fun createDeviceInput(device: AVCaptureDevice): AVCaptureDeviceInput =
    throwingNSError("Failed to create an input for device \"${device.uniqueID}\"") { error ->
        AVCaptureDeviceInput.deviceInputWithDevice(device, error)
    }

/**
 * Adds a listener for the notification with the given [name] posted by this session, called on the main queue.
 */
internal fun AVCaptureSession.addListener(name: String?, listener: (NSNotification?) -> Unit): ListenerSubscription {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(
        name = name,
        `object` = this,
        queue = NSOperationQueue.mainQueue,
        usingBlock = listener,
    )
    return ListenerSubscription { center.removeObserver(observer) }
}
