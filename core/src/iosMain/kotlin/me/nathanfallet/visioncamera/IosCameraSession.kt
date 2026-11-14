package me.nathanfallet.visioncamera

import platform.AVFAudio.AVAudioSession
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMultiCamSession
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionDidStartRunningNotification
import platform.AVFoundation.AVCaptureSessionDidStopRunningNotification
import platform.AVFoundation.AVCaptureSessionErrorKey
import platform.AVFoundation.AVCaptureSessionInterruptionEndedNotification
import platform.AVFoundation.AVCaptureSessionInterruptionReasonKey
import platform.AVFoundation.AVCaptureSessionPresetInputPriority
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureSessionWasInterruptedNotification
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.setExposureTargetBias
import platform.AVFoundation.videoZoomFactor
import platform.Foundation.NSError
import platform.Foundation.NSNumber

/**
 * The iOS [CameraSession], backed by an [AVCaptureSession] or an [AVCaptureMultiCamSession] (port of V5's
 * `HybridCameraSession.swift`).
 *
 * All configuration and start/stop calls run on a dedicated serial dispatch queue.
 */
@Suppress("TooManyFunctions")
internal class IosCameraSession(enableMultiCam: Boolean) : CameraSession {
    private val session: AVCaptureSession = if (enableMultiCam) AVCaptureMultiCamSession() else AVCaptureSession()

    init {
        // We manage formats manually via `device.activeFormat`, so opt out of the
        // session's preset-based format selection upfront.
        session.sessionPreset = AVCaptureSessionPresetInputPriority
    }

    override val isRunning: Boolean get() = session.running

    // region Configuration

    override suspend fun configure(
        connections: List<CameraSessionConnection>,
        config: CameraSessionConfiguration,
    ): List<CameraController> = queue.await {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != AVAuthorizationStatusAuthorized) {
            throw VisionCameraException("Camera Permission not yet granted!")
        }

        // Ensure multi-cam is enabled if we have multiple inputs
        val isMultiCam = connections.size > 1
        if (isMultiCam && session !is AVCaptureMultiCamSession) {
            throw VisionCameraException(
                "Cannot add multiple inputs ($connections) to a single-cam CameraSession! Create your CameraSession " +
                    "as a multi-cam session (`enableMultiCam = true`) to add multiple camera inputs.",
            )
        }

        Logger.info("Reconfiguring CameraSession with ${connections.size} connection(s)...")

        // Resolve flat Connections upfront to AVCaptureDevice, AVCaptureOutput, etc
        val resolvedConnections = connections.map { ResolvedCameraSessionConnection(it) }

        // Wrap the configuration in a batch
        session.beginConfiguration()
        try {
            applyConfiguration(resolvedConnections, config, isMultiCam)
        } finally {
            session.commitConfiguration()
        }
        Logger.info("Successfully applied CameraSession configuration for ${connections.size} connection(s)!")

        // Return CameraControllers per connection to adjust camera settings (zoom, etc)
        resolvedConnections.map { IosCameraController(device = it.input, queue = queue) }
    }

    private fun applyConfiguration(
        connections: List<ResolvedCameraSessionConnection>,
        config: CameraSessionConfiguration,
        isMultiCam: Boolean,
    ) {
        // Detach all unwanted preview layers before touching inputs/outputs
        detachUnwantedPreviewLayers(connections)
        // Remove all unwanted inputs and add all new inputs
        updateInputs(connections)
        // Remove all unwanted outputs and add all new outputs
        updateOutputs(connections)
        // Remove all unwanted connections and add all new connections
        updateConnections(connections)

        // Configure all individual inputs/outputs/connections (e.g. orientation/mirrorMode)
        for (connection in connections) {
            configureConnection(connection, isMultiCam)
            configureOutputs(connection)
        }

        // If we have any Dynamic Range Constraints, we disable automatic color space selection on the
        // AVCaptureSession, as we cannot automatically adjust the ColorSpace then.
        val hasCustomDynamicRangeConstraint = connections.any { connection ->
            connection.constraints.any { it is VideoDynamicRangeConstraint }
        }
        session.automaticallyConfiguresCaptureDeviceForWideColor = !hasCustomDynamicRangeConstraint

        // Haptics and System Sounds Playback
        val audioSession = AVAudioSession.sharedInstance()
        if (audioSession.allowHapticsAndSystemSoundsDuringRecording != config.allowHapticsAndSystemSoundsPlayback) {
            throwingNSError("Failed to configure haptics and system sounds playback") { error ->
                audioSession.setAllowHapticsAndSystemSoundsDuringRecording(
                    config.allowHapticsAndSystemSoundsPlayback,
                    error,
                )
            }
        }
        // Background Audio Playback
        if (isIOSAtLeast(major = 18)) {
            session.configuresApplicationAudioSessionToMixWithOthers = config.allowBackgroundAudioPlayback
        }
    }

    private fun configureConnection(connection: ResolvedCameraSessionConnection, isMultiCam: Boolean) {
        val resolved = ConstraintResolver.resolveConstraints(
            device = connection.input,
            constraints = connection.constraints,
            outputs = connection.outputs.map { it.output.cameraOutput },
            isMultiCam = isMultiCam,
        )

        // Notify the callback that we resolved the constraints to a specific `config`
        connection.onSessionConfigSelected?.invoke(
            IosCameraSessionConfig(
                negotiatedFormat = resolved.negotiatedFormat,
                enabledConstraints = resolved.enabledConstraints,
            ),
        )

        // Lock for a device config change...
        val device = resolved.device
        device.lockForConfigurationOrThrow()
        try {
            session.applyResolvedConstraints(device, resolved)
        } finally {
            device.unlockForConfiguration()
        }
    }

    private fun configureOutputs(connection: ResolvedCameraSessionConnection) {
        for (outputConfiguration in connection.outputs) {
            val config = OutputConfiguration(mirrorMode = outputConfiguration.mirrorMode)
            when (val output = outputConfiguration.output) {
                is ResolvedCameraSessionConnection.Output.Capture -> output.output.configure(config)
                is ResolvedCameraSessionConnection.Output.Preview -> output.preview.configure(config)
            }
        }
    }

    // endregion

    // region Start/Stop

    override suspend fun start() = queue.await {
        if (!session.running) session.startRunning()
    }

    override suspend fun stop() = queue.await {
        if (session.running) session.stopRunning()
    }

    // endregion

    // region Helpers

    /**
     * Detach all preview layers that are not listed in the [targetConnections] from this session. This must run
     * before [updateInputs] or [updateOutputs], as those methods kill preview connections without unsetting the
     * `session`.
     */
    private fun detachUnwantedPreviewLayers(targetConnections: List<ResolvedCameraSessionConnection>) {
        val currentlyAttachedPreviewLayers = session.sessionConnections.mapNotNull { it.videoPreviewLayer }
        for (previewLayer in currentlyAttachedPreviewLayers) {
            if (targetConnections.none { it.isConnectedTo(previewLayer) }) {
                Logger.info("Removing preview $previewLayer...")
                previewLayer.session = null
            }
        }
    }

    /**
     * Adds all inputs of the given [targetConnections] if they haven't been added yet, and removes all current inputs
     * that aren't listed in the [targetConnections].
     */
    private fun updateInputs(targetConnections: List<ResolvedCameraSessionConnection>) {
        val requiresAudioInput = targetConnections.any { it.requiresAudioInput }

        // 1. Loop through all existing inputs in the session - if it's not in our connections, we remove it
        for (currentlyAttachedInput in session.sessionInputs) {
            val isWanted = if (currentlyAttachedInput.isMicrophone) {
                // It's a microphone/audio input - let's check if we want audio in any connection.
                requiresAudioInput
            } else {
                // It's a normal camera device - let's check if we want it in connections.
                targetConnections.any { it.isConnectedTo(currentlyAttachedInput) }
            }
            if (!isWanted) {
                Logger.info("Removing input $currentlyAttachedInput...")
                session.removeInput(currentlyAttachedInput)
            }
        }

        // 2. Loop through all connection inputs - if it's not yet attached to the session, add it
        for (connection in targetConnections) {
            val device = connection.input.device
            val containsInput = session.sessionInputs.any { (it as? AVCaptureDeviceInput)?.device == device }
            if (!containsInput) {
                // 2.1. It doesn't exist yet - add it to the session...
                val deviceInput = createDeviceInput(device)
                Logger.info("Adding input $deviceInput...")
                session.addInputWithNoConnections(deviceInput)
                // 2.2. Configure the input with initial settings
                if (connection.initialZoom != null || connection.initialExposureBias != null) {
                    applyInitialConfig(device, connection.initialZoom, connection.initialExposureBias)
                }
            }
        }

        // 3. If we need an audio input, add it now
        if (requiresAudioInput && session.sessionInputs.none { it.isMicrophone }) {
            if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) != AVAuthorizationStatusAuthorized) {
                throw VisionCameraException("Audio Permission not yet granted!")
            }
            session.addAudioInput()
        }
    }

    /**
     * Adds all outputs of the given [targetConnections] if they haven't been added yet, and removes all current
     * outputs that aren't listed in the [targetConnections].
     */
    private fun updateOutputs(targetConnections: List<ResolvedCameraSessionConnection>) {
        // 1. Loop through all current session outputs - if it's not in our connections, we remove it
        for (currentlyAttachedOutput in session.sessionOutputs) {
            if (targetConnections.none { it.isConnectedTo(currentlyAttachedOutput) }) {
                Logger.info("Removing output $currentlyAttachedOutput...")
                session.removeOutput(currentlyAttachedOutput)
            }
        }
        // 2. Loop through all outputs of all connections - if it's not yet attached to the session, add it
        for (connection in targetConnections) {
            for (output in connection.outputs) {
                if (!session.containsOutput(output.output)) {
                    session.addOutputWithNoConnections(output.output)
                }
            }
        }
    }

    /**
     * Removes all unwanted connections and adds all new connections. By this time, [updateInputs] and [updateOutputs]
     * have already been called, so all unwanted inputs or outputs (and their connections) have been removed.
     */
    private fun updateConnections(targetConnections: List<ResolvedCameraSessionConnection>) {
        // 1. Loop through all current session connections - if it's not in our connections, we remove it
        for (currentConnection in session.sessionConnections) {
            if (targetConnections.none { it.contains(currentConnection) }) {
                Logger.info("Removing connection $currentConnection...")
                session.removeConnection(currentConnection)
            }
        }

        // 2. Add all new connections that we don't have yet
        for (connection in targetConnections) {
            for (outputConfiguration in connection.outputs) {
                addConnectionIfNeeded(connection.input.device, outputConfiguration.output)
            }
        }
    }

    private fun addConnectionIfNeeded(input: AVCaptureDevice, output: ResolvedCameraSessionConnection.Output) {
        // 1. Add the camera -> output connection
        if (!session.containsConnection(input = input, output = output)) {
            session.addConnection(input = input, output = output)
        }
        // 2. If this `output` requires audio, add this connection too
        if (output is ResolvedCameraSessionConnection.Output.Capture && output.requiresAudioInput) {
            val captureOutput = output.output.output
            if (!session.containsAudioConnection(captureOutput)) {
                session.addAudioConnection(captureOutput)
            }
        }
    }

    private fun applyInitialConfig(device: AVCaptureDevice, initialZoom: Double?, initialExposureBias: Double?) {
        device.lockForConfigurationOrThrow()
        try {
            initialZoom?.let {
                device.videoZoomFactor =
                    it.coerceIn(device.minAvailableVideoZoomFactor, device.maxAvailableVideoZoomFactor)
            }
            initialExposureBias?.let {
                val clamped = it.toFloat().coerceIn(device.minExposureTargetBias, device.maxExposureTargetBias)
                device.setExposureTargetBias(clamped, completionHandler = null)
            }
        } finally {
            device.unlockForConfiguration()
        }
    }

    // endregion

    // region Listeners

    override fun addOnStartedListener(onStarted: () -> Unit): ListenerSubscription =
        session.addListener(AVCaptureSessionDidStartRunningNotification) { onStarted() }

    override fun addOnStoppedListener(onStopped: () -> Unit): ListenerSubscription =
        session.addListener(AVCaptureSessionDidStopRunningNotification) { onStopped() }

    override fun addOnErrorListener(onError: (error: Throwable) -> Unit): ListenerSubscription =
        session.addListener(AVCaptureSessionRuntimeErrorNotification) { notification ->
            val error = notification?.userInfo?.get(AVCaptureSessionErrorKey) as? NSError
            onError(
                error?.toException() ?: VisionCameraException(
                    "CameraSession encountered an Error, but the payload didn't contain an `AVCaptureSessionErrorKey`!",
                ),
            )
        }

    override fun addOnInterruptionStartedListener(
        onInterruptionStarted: (reason: InterruptionReason) -> Unit,
    ): ListenerSubscription = session.addListener(AVCaptureSessionWasInterruptedNotification) { notification ->
        val reason = notification?.userInfo?.get(AVCaptureSessionInterruptionReasonKey) as? NSNumber
        onInterruptionStarted(reason?.let { interruptionReasonOf(it.longValue) } ?: InterruptionReason.UNKNOWN)
    }

    override fun addOnInterruptionEndedListener(onInterruptionEnded: () -> Unit): ListenerSubscription =
        session.addListener(AVCaptureSessionInterruptionEndedNotification) { onInterruptionEnded() }

    // endregion

    private companion object {
        /** The serial queue all session and device operations run on, shared by all sessions like in V5. */
        val queue = createSerialQueue("me.nathanfallet.visioncamera.session")
    }
}
