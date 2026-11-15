package me.nathanfallet.visioncamera.skia.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.FocusOptions
import me.nathanfallet.visioncamera.MeteringMode
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.VisionCamera
import me.nathanfallet.visioncamera.VisionCameraException
import me.nathanfallet.visioncamera.skia.makeNonTextureImage
import me.nathanfallet.visioncamera.skia.transformPoint
import org.jetbrains.skia.Image

/**
 * A reference to a [SkiaCamera] (the equivalent of a `ref` on V5's `<SkiaCamera />`, `SkiaCameraRef`). Create it with
 * [rememberSkiaCameraRef] and pass it to [SkiaCamera].
 *
 * ```kotlin
 * val camera = rememberSkiaCameraRef()
 * val scope = rememberCoroutineScope()
 * SkiaCamera(
 *     device = TargetCameraPosition.BACK,
 *     isActive = true,
 *     ref = camera,
 *     onFrame = { frame, render ->
 *         render { (frameTexture, canvas) -> canvas.drawImage(frameTexture, 0f, 0f) }
 *         frame.dispose()
 *     },
 *     modifier = Modifier.pointerInput(Unit) {
 *         detectTapGestures { offset -> scope.launch { camera.focusTo(offset) } }
 *     },
 * )
 * Button(onClick = {
 *     val snapshot = camera.takeSnapshot()
 *     println("Captured Skia snapshot! ${snapshot?.imageInfo}")
 * }) { Text("Snapshot") }
 * ```
 *
 * View coordinates are in Compose pixels, as in pointer events.
 */
@Stable
class SkiaCameraRef internal constructor() {
    /**
     * The current [CameraController], or `null` if the Camera is not ready yet. It is set once the session is
     * configured, and may change over time.
     */
    var controller: CameraController? by mutableStateOf(null)
        internal set

    /**
     * The graphics layer the [SkiaCamera] draws its rendered Frames into (V5's Skia `Canvas` ref), or `null` if it
     * has not been laid out yet. It is set after the first composition, and usually won't change. For example,
     * `canvas?.toImageBitmap()` captures what is currently displayed.
     */
    var canvas: GraphicsLayer? by mutableStateOf(null)
        internal set

    internal var state: SkiaCameraState? = null

    private fun requireController(): CameraController =
        controller ?: throw VisionCameraException("Cannot focus - Camera is null!")

    /**
     * Converts the given [viewPoint] (in the [SkiaCamera]'s coordinate system) to a normalized [Point] in the Camera's
     * coordinate system, with values ranging from `0...1`.
     *
     * @throws VisionCameraException If the [SkiaCamera] has not been laid out yet, or no Frame has been rendered yet.
     */
    fun convertViewPointToNormalizedPoint(viewPoint: Offset): Point {
        val state = state
        val size = state?.canvasSize ?: IntSize.Zero
        if (state == null || size.width <= 0 || size.height <= 0) {
            throw VisionCameraException("Cannot focus - Skia Canvas has not been laid out yet!")
        }
        val transform = state.lastFrameTransform
            ?: throw VisionCameraException("Cannot focus - no Frame has been rendered yet!")
        val normalizedPoint = Point(viewPoint.x.toDouble() / size.width, viewPoint.y.toDouble() / size.height)
        return transformPoint(normalizedPoint, transform)
    }

    /**
     * Focuses the Camera pipeline to the specified [viewPoint] relative to the [SkiaCamera]'s coordinate system, using
     * the specified [MeteringMode]s of the [options].
     *
     * ```kotlin
     * // Focus center
     * camera.focusTo(Offset(width / 2f, height / 2f))
     * ```
     *
     * @throws VisionCameraException If the Camera is not ready yet, or no Frame has been rendered yet.
     */
    suspend fun focusTo(viewPoint: Offset, options: FocusOptions = FocusOptions()) {
        val controller = requireController()
        val point = convertViewPointToNormalizedPoint(viewPoint)
        controller.focusTo(VisionCamera.createNormalizedMeteringPoint(point.x, point.y), options)
    }

    /**
     * Resets the focus, see [CameraController.resetFocus].
     *
     * @throws VisionCameraException If the Camera is not ready yet.
     */
    suspend fun resetFocus() {
        val controller = controller ?: throw VisionCameraException("Cannot reset focus - Camera is null!")
        controller.resetFocus()
    }

    /**
     * Takes a snapshot of the currently rendered content, or returns `null` if none is available. It must be called
     * on the main thread. The caller owns the returned image, and closes it when done with it.
     *
     * ```kotlin
     * val snapshot = camera.takeSnapshot()
     * println("Captured Skia snapshot! ${snapshot?.imageInfo}")
     * ```
     */
    fun takeSnapshot(): Image? = state?.previewFrame?.image?.makeNonTextureImage()
}

/**
 * Create a [SkiaCameraRef], kept across recompositions.
 */
@Composable
fun rememberSkiaCameraRef(): SkiaCameraRef = remember { SkiaCameraRef() }
