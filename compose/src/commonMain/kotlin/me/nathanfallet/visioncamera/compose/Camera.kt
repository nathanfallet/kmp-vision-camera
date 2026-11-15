package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.CameraControllerConfiguration
import me.nathanfallet.visioncamera.CameraDeviceOrPosition
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.CameraSessionConfig
import me.nathanfallet.visioncamera.CameraSessionConfiguration
import me.nathanfallet.visioncamera.Constraint
import me.nathanfallet.visioncamera.InterruptionReason
import me.nathanfallet.visioncamera.MirrorMode
import me.nathanfallet.visioncamera.OrientationSource
import me.nathanfallet.visioncamera.PreviewImplementationMode
import me.nathanfallet.visioncamera.PreviewResizeMode
import me.nathanfallet.visioncamera.TorchMode

/**
 * A Camera view (the equivalent of VisionCamera's `<Camera />`): it creates a preview output, runs a session with
 * it and the given [outputs] (see [rememberCamera]), and displays the preview.
 *
 * ```kotlin
 * Camera(
 *     device = TargetCameraPosition.BACK,
 *     isActive = true,
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * @param device The [me.nathanfallet.visioncamera.CameraDevice] to open, or a
 * [me.nathanfallet.visioncamera.TargetCameraPosition] to use the default device at that position.
 * @param isActive Starts the session when `true`, and stops it when `false`.
 * @param modifier The [Modifier] of the preview.
 * @param outputs Additional [CameraOutput]s, besides the preview.
 * @param constraints [Constraint]s the session tries to match, ranked by priority.
 * @param mirrorMode Whether the outputs are mirrored. [MirrorMode.AUTO] mirrors selfie cameras.
 * @param orientationSource The [OrientationSource] whose orientation is automatically applied to all outputs (see
 * [CameraOutput.outputOrientation]), or `null` to set it yourself (VisionCamera's `'custom'`).
 * @param zoom The zoom value, applied declaratively.
 * @param exposure The exposure bias, applied declaratively.
 * @param torchMode The torch mode, applied declaratively.
 * @param controllerConfiguration Low-light boost, smooth auto-focus and distortion correction.
 * @param sessionConfiguration Session-wide configuration (iOS audio and haptics behavior).
 * @param resizeMode How the Camera feed is scaled into the view's bounds.
 * @param implementationMode Whether the preview uses a `SurfaceView` or a `TextureView` (Android only).
 * @param onControllerChanged Called with the [CameraController] once the session is configured, e.g. to focus.
 * @param onSessionConfigSelected Called once the [constraints] have been resolved.
 * @param onConfigured Called whenever the session has been configured.
 * @param onStarted Called when the session started.
 * @param onStopped Called when the session stopped.
 * @param onError Called when the session encountered an error.
 * @param onInterruptionStarted Called when the session was interrupted.
 * @param onInterruptionEnded Called when an interruption ended.
 * @param onSubjectAreaChanged Called when the subject area substantially changes, e.g. when the user pans away from a
 * scene previously in focus: a good point to reset locked focus. iOS only, like V5.
 * @param onUIRotationChanged Called when the output orientation (driven by [orientationSource]) or the interface
 * orientation changes, with the rotation in degrees (`-90`, `0`, `90` or `180`) that UI elements such as Camera
 * controls need to appear upright.
 * @param onPreviewStarted Called when the preview started streaming.
 * @param onPreviewStopped Called when the preview stopped streaming.
 * @param enableNativeZoomGesture Installs the native pinch-to-zoom gesture. [zoom] must then be `null`.
 * @param enableNativeTapToFocusGesture Installs the native tap-to-focus gesture.
 * @param ref A [CameraRef] exposing the controller and preview methods, see [rememberCameraRef].
 */
@Suppress("LongParameterList")
@Composable
fun Camera(
    device: CameraDeviceOrPosition,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    outputs: List<CameraOutput> = emptyList(),
    constraints: List<Constraint> = emptyList(),
    mirrorMode: MirrorMode = MirrorMode.AUTO,
    orientationSource: OrientationSource? = OrientationSource.DEVICE,
    zoom: Double? = null,
    exposure: Double? = null,
    torchMode: TorchMode? = null,
    controllerConfiguration: CameraControllerConfiguration = CameraControllerConfiguration(),
    sessionConfiguration: CameraSessionConfiguration = CameraSessionConfiguration(),
    resizeMode: PreviewResizeMode = PreviewResizeMode.COVER,
    implementationMode: PreviewImplementationMode = PreviewImplementationMode.PERFORMANCE,
    onControllerChanged: ((CameraController?) -> Unit)? = null,
    onSessionConfigSelected: ((CameraSessionConfig) -> Unit)? = null,
    onConfigured: (() -> Unit)? = null,
    onStarted: (() -> Unit)? = null,
    onStopped: (() -> Unit)? = null,
    onError: (Throwable) -> Unit = { println("VisionCamera error: $it") },
    onInterruptionStarted: ((InterruptionReason) -> Unit)? = null,
    onInterruptionEnded: (() -> Unit)? = null,
    onSubjectAreaChanged: (() -> Unit)? = null,
    onUIRotationChanged: ((rotation: Int) -> Unit)? = null,
    onPreviewStarted: (() -> Unit)? = null,
    onPreviewStopped: (() -> Unit)? = null,
    enableNativeZoomGesture: Boolean = false,
    enableNativeTapToFocusGesture: Boolean = false,
    ref: CameraRef? = null,
) {
    require(!enableNativeZoomGesture || zoom == null) {
        "`zoom` must not be set if `enableNativeZoomGesture` is enabled!"
    }

    // 1. Create a PreviewOutput for the Camera
    val previewOutput = rememberPreviewOutput()

    // 2. Create the session and controller, with the preview output
    val controller = rememberCamera(
        isActive = isActive,
        device = device,
        outputs = listOf(previewOutput) + outputs,
        constraints = constraints,
        mirrorMode = mirrorMode,
        orientationSource = orientationSource,
        zoom = zoom,
        exposure = exposure,
        torchMode = torchMode,
        controllerConfiguration = controllerConfiguration,
        sessionConfiguration = sessionConfiguration,
        onSessionConfigSelected = onSessionConfigSelected,
        onConfigured = onConfigured,
        onStarted = onStarted,
        onStopped = onStopped,
        onError = onError,
        onInterruptionStarted = onInterruptionStarted,
        onInterruptionEnded = onInterruptionEnded,
        onSubjectAreaChanged = onSubjectAreaChanged,
        onUIRotationChanged = onUIRotationChanged,
    )
    LaunchedEffect(controller) { onControllerChanged?.invoke(controller) }
    SideEffect { ref?.controller = controller }

    // 3. Attach the native gesture controllers
    val gestureControllers = rememberGestureControllers(
        controller = controller,
        enableNativeTapToFocusGesture = enableNativeTapToFocusGesture,
        enableNativeZoomGesture = enableNativeZoomGesture,
    )

    // 4. Display the preview
    NativePreviewView(
        previewOutput = previewOutput,
        modifier = modifier,
        resizeMode = resizeMode,
        implementationMode = implementationMode,
        gestureControllers = gestureControllers,
        ref = ref?.preview,
        onPreviewStarted = onPreviewStarted,
        onPreviewStopped = onPreviewStopped,
    )
}
