package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.CameraControllerConfiguration
import me.nathanfallet.visioncamera.CameraDeviceOrPosition
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.CameraOutputConfiguration
import me.nathanfallet.visioncamera.CameraSession
import me.nathanfallet.visioncamera.CameraSessionConfig
import me.nathanfallet.visioncamera.CameraSessionConfiguration
import me.nathanfallet.visioncamera.CameraSessionConnection
import me.nathanfallet.visioncamera.Constraint
import me.nathanfallet.visioncamera.InterruptionReason
import me.nathanfallet.visioncamera.MirrorMode
import me.nathanfallet.visioncamera.TorchMode
import me.nathanfallet.visioncamera.VisionCamera

/**
 * Use the Camera (the equivalent of VisionCamera's `useCamera`).
 *
 * This creates a [CameraSession], configures it with the [device] and [outputs] whenever they change, starts it
 * while [isActive] is `true`, forwards its events to the given callbacks, applies [zoom], [exposure] and
 * [torchMode] declaratively, and returns the [CameraController].
 *
 * ```kotlin
 * val previewOutput = rememberPreviewOutput()
 * val controller = rememberCamera(
 *     isActive = true,
 *     device = TargetCameraPosition.BACK,
 *     outputs = listOf(previewOutput),
 * )
 * NativePreviewView(previewOutput, Modifier.fillMaxSize())
 * ```
 *
 * @param isActive Starts the session when `true`, and stops it when `false`.
 * @param device The [me.nathanfallet.visioncamera.CameraDevice] to open, or a
 * [me.nathanfallet.visioncamera.TargetCameraPosition] to use the default device at that position.
 * @param outputs The [CameraOutput]s the [device] streams into.
 * @param constraints [Constraint]s the session tries to match, ranked by priority.
 * @param mirrorMode Whether the outputs are mirrored. [MirrorMode.AUTO] mirrors selfie cameras.
 * @param zoom The zoom value, applied declaratively.
 * @param exposure The exposure bias, applied declaratively.
 * @param torchMode The torch mode, applied declaratively.
 * @param controllerConfiguration Low-light boost, smooth auto-focus and distortion correction.
 * @param sessionConfiguration Session-wide configuration (iOS audio and haptics behavior).
 * @param onSessionConfigSelected Called once the [constraints] have been resolved.
 * @param onConfigured Called whenever the session has been configured.
 * @param onStarted Called when the session started.
 * @param onStopped Called when the session stopped.
 * @param onError Called when the session encountered an error.
 * @param onInterruptionStarted Called when the session was interrupted.
 * @param onInterruptionEnded Called when an interruption ended.
 * @return The [CameraController], or `null` while the session is not configured yet.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun rememberCamera(
    isActive: Boolean,
    device: CameraDeviceOrPosition,
    outputs: List<CameraOutput> = emptyList(),
    constraints: List<Constraint> = emptyList(),
    mirrorMode: MirrorMode = MirrorMode.AUTO,
    zoom: Double? = null,
    exposure: Double? = null,
    torchMode: TorchMode? = null,
    controllerConfiguration: CameraControllerConfiguration = CameraControllerConfiguration(),
    sessionConfiguration: CameraSessionConfiguration = CameraSessionConfiguration(),
    onSessionConfigSelected: ((CameraSessionConfig) -> Unit)? = null,
    onConfigured: (() -> Unit)? = null,
    onStarted: (() -> Unit)? = null,
    onStopped: (() -> Unit)? = null,
    onError: (Throwable) -> Unit = { println("VisionCamera error: $it") },
    onInterruptionStarted: ((InterruptionReason) -> Unit)? = null,
    onInterruptionEnded: (() -> Unit)? = null,
): CameraController? {
    val currentOnSessionConfigSelected by rememberUpdatedState(onSessionConfigSelected)
    val currentOnConfigured by rememberUpdatedState(onConfigured)
    val currentOnStarted by rememberUpdatedState(onStarted)
    val currentOnStopped by rememberUpdatedState(onStopped)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnInterruptionStarted by rememberUpdatedState(onInterruptionStarted)
    val currentOnInterruptionEnded by rememberUpdatedState(onInterruptionEnded)
    val currentZoom by rememberUpdatedState(zoom)
    val currentExposure by rememberUpdatedState(exposure)

    // 1. Create the session, and stop it when leaving the composition.
    val session = produceState<CameraSession?>(null) {
        value = runCatchingCamera(currentOnError) { VisionCamera.createCameraSession(enableMultiCam = false) }
    }.value
    DisposableEffect(session) {
        onDispose {
            if (session != null) disposalScope.launch { runCatchingCamera(currentOnError) { session.stop() } }
        }
    }

    // 2. Forward the session events.
    DisposableEffect(session) {
        val subscriptions = listOfNotNull(
            session?.addOnStartedListener { currentOnStarted?.invoke() },
            session?.addOnStoppedListener { currentOnStopped?.invoke() },
            session?.addOnErrorListener { currentOnError(it) },
            session?.addOnInterruptionStartedListener { currentOnInterruptionStarted?.invoke(it) },
            session?.addOnInterruptionEndedListener { currentOnInterruptionEnded?.invoke() },
        )
        onDispose { subscriptions.forEach { it.remove() } }
    }

    // 3. Configure the session with the input and outputs to get a CameraController.
    var controller by remember(session) { mutableStateOf<CameraController?>(null) }
    LaunchedEffect(session, device, outputs, constraints, mirrorMode, sessionConfiguration) {
        if (session == null) return@LaunchedEffect
        val connection = CameraSessionConnection(
            input = device,
            outputs = outputs.map { CameraOutputConfiguration(it, mirrorMode) },
            constraints = constraints,
            initialZoom = currentZoom,
            initialExposureBias = currentExposure,
            onSessionConfigSelected = { currentOnSessionConfigSelected?.invoke(it) },
        )
        controller = runCatchingCamera(currentOnError) {
            session.configure(listOf(connection), sessionConfiguration).firstOrNull()
        }
        currentOnConfigured?.invoke()
    }

    // 4. Configure the controller.
    LaunchedEffect(controller, controllerConfiguration) {
        controller?.let { runCatchingCamera(currentOnError) { it.configure(controllerConfiguration) } }
    }

    // 5. Start (or stop) the session if we have a controller and isActive is true.
    val shouldRun = isActive && controller != null
    LaunchedEffect(session, shouldRun) {
        if (session == null) return@LaunchedEffect
        runCatchingCamera(currentOnError) { if (shouldRun) session.start() else session.stop() }
    }

    // 6. Update the controller's declarative props.
    LaunchedEffect(controller, zoom) {
        if (zoom != null) controller?.let { runCatchingCamera(currentOnError) { it.setZoom(zoom) } }
    }
    LaunchedEffect(controller, exposure) {
        if (exposure != null) controller?.let { runCatchingCamera(currentOnError) { it.setExposureBias(exposure) } }
    }
    LaunchedEffect(controller, torchMode) {
        if (torchMode != null) controller?.let { runCatchingCamera(currentOnError) { it.setTorchMode(torchMode) } }
    }

    // 7. Give the user the controller.
    return controller
}

/**
 * Stopping a session must outlive the composition that started it.
 */
private val disposalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runCatchingCamera(onError: (Throwable) -> Unit, block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}
