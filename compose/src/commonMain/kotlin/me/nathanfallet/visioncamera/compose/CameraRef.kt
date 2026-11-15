package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.FocusOptions
import me.nathanfallet.visioncamera.MeteringPoint
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.VisionCameraException

/**
 * A reference to a [Camera], exposing its [controller] and preview methods (the equivalent of a `ref` on
 * VisionCamera's `<Camera />`). Create it with [rememberCameraRef] and pass it to [Camera].
 *
 * ```kotlin
 * val camera = rememberCameraRef()
 * val scope = rememberCoroutineScope()
 * Camera(
 *     device = TargetCameraPosition.BACK,
 *     isActive = true,
 *     ref = camera,
 *     modifier = Modifier.pointerInput(Unit) {
 *         detectTapGestures { offset -> scope.launch { camera.focusTo(offset) } }
 *     },
 * )
 * ```
 *
 * View coordinates are in Compose pixels, as in pointer events.
 */
@Stable
class CameraRef internal constructor() {
    /** The [CameraController] of the [Camera], or `null` if it is not ready yet. */
    var controller: CameraController? by mutableStateOf(null)
        internal set

    /** The [PreviewViewRef] of the [Camera]'s preview. */
    val preview: PreviewViewRef = PreviewViewRef()

    private fun requireController(): CameraController =
        controller ?: throw VisionCameraException("Camera is not yet ready!")

    /** See [CameraController.startZoomAnimation]. */
    suspend fun startZoomAnimation(zoom: Double, rate: Double) = requireController().startZoomAnimation(zoom, rate)

    /** See [CameraController.cancelZoomAnimation]. */
    suspend fun cancelZoomAnimation() = requireController().cancelZoomAnimation()

    /**
     * Focuses on the given point of the preview (e.g. a tap), see [CameraController.focusTo].
     *
     * @throws VisionCameraException If the camera or its preview is not ready yet.
     */
    suspend fun focusTo(viewPoint: Offset, options: FocusOptions = FocusOptions()) {
        val controller = requireController()
        controller.focusTo(preview.createMeteringPoint(viewPoint), options)
    }

    /** See [CameraController.resetFocus]. */
    suspend fun resetFocus() = requireController().resetFocus()

    /** See [PreviewViewRef.createMeteringPoint]. */
    fun createMeteringPoint(viewPoint: Offset, size: Float? = null): MeteringPoint =
        preview.createMeteringPoint(viewPoint, size)

    /** See [PreviewViewRef.convertCameraPointToViewPoint]. */
    fun convertCameraPointToViewPoint(cameraPoint: Point): Offset = preview.convertCameraPointToViewPoint(cameraPoint)

    /** See [PreviewViewRef.convertViewPointToCameraPoint]. */
    fun convertViewPointToCameraPoint(viewPoint: Offset): Point = preview.convertViewPointToCameraPoint(viewPoint)
}

/**
 * Create a [CameraRef], kept across recompositions.
 */
@Composable
fun rememberCameraRef(): CameraRef = remember { CameraRef() }
