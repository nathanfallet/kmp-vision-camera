package me.nathanfallet.visioncamera

/**
 * The reason a [CameraSession] was interrupted.
 */
enum class InterruptionReason {
    /** The app went to the background. */
    VIDEO_DEVICE_NOT_AVAILABLE_IN_BACKGROUND,

    /** The microphone is used by another app. */
    AUDIO_DEVICE_IN_USE_BY_ANOTHER_CLIENT,

    /** The camera is used by another app. */
    VIDEO_DEVICE_IN_USE_BY_ANOTHER_CLIENT,

    /** The camera is not available while multiple apps are in the foreground (e.g. split screen). */
    VIDEO_DEVICE_NOT_AVAILABLE_WITH_MULTIPLE_FOREGROUND_APPS,

    /** The camera is not available due to system pressure (e.g. the phone overheated). */
    VIDEO_DEVICE_NOT_AVAILABLE_DUE_TO_SYSTEM_PRESSURE,

    /** Sensitive content mitigation has been activated. */
    SENSITIVE_CONTENT_MITIGATION_ACTIVATED,

    /** Unknown reason. */
    UNKNOWN,
}

/**
 * Represents a Camera Session.
 *
 * A Camera Session has [CameraSessionConnection]s to stream from an input device ([CameraDevice]) to outputs
 * ([CameraOutput]). It is recommended to configure the session before starting it to avoid reconfiguration
 * hiccups.
 *
 * ```kotlin
 * val session = VisionCamera.createCameraSession(enableMultiCam = false)
 * val (controller) = session.configure(
 *     listOf(
 *         CameraSessionConnection(
 *             input = device,
 *             outputs = listOf(
 *                 CameraOutputConfiguration(previewOutput),
 *                 CameraOutputConfiguration(photoOutput),
 *             ),
 *         ),
 *     ),
 * )
 * session.start()
 * ```
 */
interface CameraSession {
    /** Whether this [CameraSession] is currently running. */
    val isRunning: Boolean

    /**
     * Configures the [CameraSession] with the given [connections], and returns one [CameraController] per
     * [CameraSessionConnection].
     *
     * Multiple connections are only allowed in a multi-cam session, with device combinations listed in
     * [CameraDeviceFactory.supportedMultiCamDeviceCombinations].
     *
     * @throws VisionCameraException If Camera permission has not been granted, if multiple [connections] are given
     * to a single-cam session, or if the configuration is not supported.
     */
    suspend fun configure(
        connections: List<CameraSessionConnection>,
        config: CameraSessionConfiguration = CameraSessionConfiguration(),
    ): List<CameraController>

    /**
     * Starts the [CameraSession].
     */
    suspend fun start()

    /**
     * Stops the [CameraSession].
     */
    suspend fun stop()

    /** Adds a listener called when the [CameraSession] started running. */
    fun addOnStartedListener(onStarted: () -> Unit): ListenerSubscription

    /** Adds a listener called when the [CameraSession] stopped running. */
    fun addOnStoppedListener(onStopped: () -> Unit): ListenerSubscription

    /** Adds a listener called when the [CameraSession] encountered an error. */
    fun addOnErrorListener(onError: (error: Throwable) -> Unit): ListenerSubscription

    /**
     * Adds a listener called when the [CameraSession] was interrupted, for example when the phone overheated, or
     * the user received a call.
     */
    fun addOnInterruptionStartedListener(onInterruptionStarted: (reason: InterruptionReason) -> Unit): ListenerSubscription

    /** Adds a listener called when an interruption has ended and the Camera runs again. */
    fun addOnInterruptionEndedListener(onInterruptionEnded: () -> Unit): ListenerSubscription
}
