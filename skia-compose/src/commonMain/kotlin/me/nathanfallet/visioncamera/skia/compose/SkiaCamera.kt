package me.nathanfallet.visioncamera.skia.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.CameraControllerConfiguration
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraDeviceOrPosition
import me.nathanfallet.visioncamera.CameraFrameOutput
import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.CameraPosition
import me.nathanfallet.visioncamera.CameraSessionConfig
import me.nathanfallet.visioncamera.CameraSessionConfiguration
import me.nathanfallet.visioncamera.Constraint
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.FrameOutputOptions
import me.nathanfallet.visioncamera.InterruptionReason
import me.nathanfallet.visioncamera.MirrorMode
import me.nathanfallet.visioncamera.OrientationSource
import me.nathanfallet.visioncamera.ResolutionBiasConstraint
import me.nathanfallet.visioncamera.Size
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.TargetVideoPixelFormat
import me.nathanfallet.visioncamera.TorchMode
import me.nathanfallet.visioncamera.VideoPixelFormat
import me.nathanfallet.visioncamera.compose.rememberCamera
import me.nathanfallet.visioncamera.compose.rememberFrameOutput
import me.nathanfallet.visioncamera.compose.rememberOrientation
import me.nathanfallet.visioncamera.skia.SkiaOnFrameState
import me.nathanfallet.visioncamera.skia.Transform
import me.nathanfallet.visioncamera.skia.clearSurfacesCache
import me.nathanfallet.visioncamera.skia.makeNonTextureImage
import me.nathanfallet.visioncamera.skia.renderToTexture
import org.jetbrains.skia.Image

/**
 * Renders a [Frame] with the given draw function: the `render` argument of [SkiaCamera]'s `onFrame`.
 */
typealias SkiaRender = (onDraw: (state: SkiaOnFrameState) -> Unit) -> Unit

/**
 * A Camera view that uses [Skia](https://skia.org) (through Skiko) for rendering (the equivalent of V5's
 * `<SkiaCamera />`).
 *
 * Instead of a native preview view, the [SkiaCamera] uses a [CameraFrameOutput] to stream [Frame]s, which are
 * converted to Skia textures (see [SkiaOnFrameState.frameTexture]) to be renderable.
 *
 * You must ensure that the [Frame] is actually rendered to the canvas inside your [onFrame] callback, by calling its
 * `render` function, otherwise it'll just be a blank view.
 *
 * Simple rendering _as-is_:
 * ```kotlin
 * SkiaCamera(
 *     device = TargetCameraPosition.BACK,
 *     isActive = true,
 *     onFrame = { frame, render ->
 *         render { (frameTexture, canvas) ->
 *             canvas.drawImage(frameTexture, 0f, 0f)
 *         }
 *         frame.dispose()
 *     },
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * Drawing Frames with a custom pass-through shader:
 * ```kotlin
 * // Simple pass-through Shader (just renders pixels as-is)
 * val effect = RuntimeEffect.makeForShader("""
 *     uniform shader src;
 *     half4 main(float2 xy) {
 *         return src.eval(xy);
 *     }
 * """)
 * val paint = Paint()
 *
 * SkiaCamera(
 *     device = TargetCameraPosition.BACK,
 *     isActive = true,
 *     onFrame = { frame, render ->
 *         render { (frameTexture, canvas) ->
 *             // Prepare the shader with the `frameTexture` as an input
 *             val imageShader = frameTexture.makeShader(
 *                 FilterTileMode.CLAMP,
 *                 FilterTileMode.CLAMP,
 *                 SamplingMode.LINEAR,
 *             )
 *             paint.shader = effect.makeShader(null, arrayOf(imageShader), null)
 *             // Draw the Frame with the shader/paint (Skia ignores the shader of a paint given to drawImage)
 *             val bounds = Rect.makeWH(frameTexture.width.toFloat(), frameTexture.height.toFloat())
 *             canvas.drawRect(bounds, paint)
 *         }
 *         frame.dispose()
 *     },
 * )
 * ```
 *
 * The rendered result is copied to the CPU and displayed by Compose, scaled to cover the view (centered and cropped).
 *
 * It is recommended to manually set a [pixelFormat], for example [TargetVideoPixelFormat.YUV] for efficiency, or
 * [TargetVideoPixelFormat.RGB] for compatibility with ML detection libraries.
 *
 * @param device The [CameraDevice] to open, or a [TargetCameraPosition] to use the default device at that position.
 * Front Cameras are mirrored.
 * @param isActive Starts the session when `true`, and stops it when `false`.
 * @param onFrame Called on every [Frame] with the [Frame] and a `render` function, on the Frame output's thread. You
 * must render the [Frame] to the canvas yourself by calling `render` once with a draw function (see
 * [SkiaOnFrameState]), otherwise it'll just be a blank view, and dispose the [Frame] afterwards.
 * @param modifier The [Modifier] of the view.
 * @param outputs Additional [CameraOutput]s, besides the Skia [CameraFrameOutput].
 * @param constraints [Constraint]s the session tries to match, ranked by priority. A [ResolutionBiasConstraint] for
 * each output is added after them, with a lower priority.
 * @param orientationSource The [OrientationSource] whose orientation is automatically applied to the additional
 * [outputs], or `null` to set it yourself. The Skia [CameraFrameOutput] always follows the interface orientation.
 * @param zoom The zoom value, applied declaratively.
 * @param exposure The exposure bias, applied declaratively.
 * @param torchMode The torch mode, applied declaratively.
 * @param controllerConfiguration Low-light boost, smooth auto-focus and distortion correction.
 * @param sessionConfiguration Session-wide configuration (iOS audio and haptics behavior).
 * @param warnIfRenderSkipped Warns the developer if the [onFrame] callback did not call `render`. Disable this if
 * you intentionally want to skip a render.
 * @param pixelFormat The [TargetVideoPixelFormat] of the Skia [CameraFrameOutput]. [TargetVideoPixelFormat.NATIVE]
 * is a zero-copy GPU-only path if the negotiated [CameraSessionConfig.nativePixelFormat] is supported by Skia (use it
 * with caution, as the device's native format could be one Skia does not support, e.g.
 * [VideoPixelFormat.RAW_BAYER_PACKED96_12_BIT]); only resort to [TargetVideoPixelFormat.YUV] (or even
 * [TargetVideoPixelFormat.RGB]) if CPU-based Frame access is required. Defaults to `YUV` on iOS and `NATIVE` on
 * Android, like V5.
 * @param enablePhysicalBufferRotation Physically rotates buffers to the target orientation and mirror mode. Disabled
 * by default, as Skia rotates and mirrors the Frame via efficient GPU-based transforms. Enabling this results in the
 * [Frame] always being upright and never mirrored.
 * @param enablePreviewSizedOutputBuffers Downscales [Frame]s to a size suitable for preview, such as the screen size.
 * This may be more efficient if the session negotiated a high-resolution output, as Skia doesn't have to operate on
 * high-resolution [Frame]s.
 * @param targetResolution The target resolution of the Skia [CameraFrameOutput], which affects both the [Frame]'s
 * size (in [onFrame]) and the preview's resolution. It is capped to the screen's size if
 * [enablePreviewSizedOutputBuffers] is enabled. As with all target resolutions, actual resolutions are negotiated
 * across constraints and outputs.
 * @param onControllerChanged Called with the [CameraController] once the session is configured, e.g. to focus.
 * @param onSessionConfigSelected Called once the [constraints] have been resolved.
 * @param onConfigured Called whenever the session has been configured.
 * @param onStarted Called when the session started.
 * @param onStopped Called when the session stopped.
 * @param onError Called when the session encountered an error.
 * @param onInterruptionStarted Called when the session was interrupted.
 * @param onInterruptionEnded Called when an interruption ended.
 * @param onSubjectAreaChanged Called when the subject area substantially changes. iOS only, like V5.
 * @param onUIRotationChanged Called when the output orientation or the interface orientation changes, with the
 * rotation in degrees (`-90`, `0`, `90` or `180`) that UI elements such as Camera controls need to appear upright.
 * @param ref A [SkiaCameraRef] exposing the controller, focus, snapshots and coordinate conversions, see
 * [rememberSkiaCameraRef].
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun SkiaCamera(
    device: CameraDeviceOrPosition,
    isActive: Boolean,
    onFrame: (frame: Frame, render: SkiaRender) -> Unit,
    modifier: Modifier = Modifier,
    outputs: List<CameraOutput> = emptyList(),
    constraints: List<Constraint> = emptyList(),
    orientationSource: OrientationSource? = OrientationSource.DEVICE,
    zoom: Double? = null,
    exposure: Double? = null,
    torchMode: TorchMode? = null,
    controllerConfiguration: CameraControllerConfiguration = CameraControllerConfiguration(),
    sessionConfiguration: CameraSessionConfiguration = CameraSessionConfiguration(),
    warnIfRenderSkipped: Boolean = true,
    pixelFormat: TargetVideoPixelFormat = defaultSkiaPixelFormat,
    enablePhysicalBufferRotation: Boolean = false,
    enablePreviewSizedOutputBuffers: Boolean = false,
    targetResolution: Size = FrameOutputOptions().targetResolution,
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
    ref: SkiaCameraRef? = null,
) {
    val state = remember { SkiaCameraState() }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentWarnIfRenderSkipped by rememberUpdatedState(warnIfRenderSkipped)

    // 1. The Frame output driving the preview: every Frame is rendered by the user, then displayed.
    val frameOutput = rememberFrameOutput(
        FrameOutputOptions(
            targetResolution = targetResolution,
            pixelFormat = pixelFormat,
            enablePreviewSizedOutputBuffers = enablePreviewSizedOutputBuffers,
            allowDeferredStart = false,
            enablePhysicalBufferRotation = enablePhysicalBufferRotation,
            dropFramesWhileBusy = true,
        ),
    ) { frame ->
        var renderCount = 0
        val render: SkiaRender = { onDraw ->
            check(renderCount < 1) { "Tried to render Frame twice! Did you call render(...) twice in your SkiaCamera?" }
            renderCount++
            renderPreviewFrame(frame, onDraw, state)
        }

        currentOnFrame(frame, render)

        if (renderCount == 0 && currentWarnIfRenderSkipped) {
            // Not rendering a Frame is fine for certain use-cases, e.g. custom FPS throttling, or intentionally
            // dropping mid-camera-flip Frames. But it can also be accidental, so we warn the user here if that happens.
            println(
                "VisionCamera: render(...) was not called - the Frame is effectively dropped from the Skia preview! " +
                    "Set warnIfRenderSkipped = false if this is intentional.",
            )
        }
    }

    // 2. The session, with the Skia Frame output (the orientation of the outputs is set below).
    val isFront = when (device) {
        is TargetCameraPosition -> device == TargetCameraPosition.FRONT
        is CameraDevice -> device.position == CameraPosition.FRONT
    }
    val controller = rememberCamera(
        isActive = isActive,
        device = device,
        outputs = outputs + frameOutput,
        constraints = constraints,
        mirrorMode = if (isFront) MirrorMode.ON else MirrorMode.AUTO,
        orientationSource = null,
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

    // 3. Update output orientations (the Skia Frame output follows the interface, as it is displayed in it)
    val otherOutputsOrientation = rememberOrientation(orientationSource) ?: CameraOrientation.UP
    val skiaFrameOutputOrientation = rememberOrientation(OrientationSource.INTERFACE) ?: CameraOrientation.UP
    LaunchedEffect(frameOutput, outputs, orientationSource, otherOutputsOrientation, skiaFrameOutputOrientation) {
        if (orientationSource != null) outputs.forEach { it.outputOrientation = otherOutputsOrientation }
        frameOutput.outputOrientation = skiaFrameOutputOrientation
    }

    // 4. On unmount, clear the Surfaces cache to free up memory.
    val currentRef by rememberUpdatedState(ref)
    DisposableEffect(state) {
        onDispose {
            state.dispose()
            clearSurfacesCache()
            // Like a React ref, the ref doesn't point to this unmounted SkiaCamera anymore.
            currentRef?.takeIf { it.state === state }?.let {
                it.state = null
                it.controller = null
                it.canvas = null
            }
        }
    }

    // 5. Display the rendered Frames.
    val graphicsLayer = rememberGraphicsLayer()
    SideEffect {
        ref?.state = state
        ref?.controller = controller
        ref?.canvas = graphicsLayer
    }
    Canvas(
        modifier
            .onSizeChanged { state.canvasSize = it }
            .drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            },
    ) {
        state.previewFrame?.draw(this)
    }
}

/**
 * Renders the [frame] with [onDraw], copies the result to the CPU as we are crossing threads, and posts it to the
 * [state]'s preview.
 */
@Suppress("TooGenericExceptionCaught")
private fun renderPreviewFrame(frame: Frame, onDraw: (state: SkiaOnFrameState) -> Unit, state: SkiaCameraState) {
    // The SkiaCamera left: don't create a new GPU context (that nothing would free) for the last Frames delivered.
    if (state.isDisposed) return
    var snapshot: Image? = null
    try {
        // 1. Render Frame to a Texture (Image) and pass custom onDraw func
        snapshot = renderToTexture(frame, onDraw)
        // 2. Copy to CPU surface as we are crossing Threads
        val snapshotCpuCopy = snapshot.makeNonTextureImage()
        val previewFrame = try {
            SkiaPreviewFrame(snapshotCpuCopy)
        } catch (e: Exception) {
            snapshotCpuCopy.close()
            throw e
        }
        // 3. Update our Preview with the currently rendered result (and info)
        state.lastFrameTransform = Transform(frame.orientation, frame.isMirrored)
        state.post(previewFrame)
    } catch (e: Exception) {
        println("VisionCamera: Failed to render! $e")
    } finally {
        snapshot?.close()
    }
}

/** The default pixel format of [SkiaCamera]: `YUV` on iOS, `NATIVE` on Android, like V5. */
internal expect val defaultSkiaPixelFormat: TargetVideoPixelFormat
