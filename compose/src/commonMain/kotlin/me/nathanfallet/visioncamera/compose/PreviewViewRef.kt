package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import me.nathanfallet.visioncamera.MeteringPoint
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.VisionCameraException

/**
 * The platform preview view, as seen from Compose: view coordinates are Compose pixels, as in pointer events.
 */
internal interface PreviewViewHandle {
    fun createMeteringPoint(viewPoint: Offset, size: Float?): MeteringPoint

    fun convertCameraPointToViewPoint(cameraPoint: Point): Offset

    fun convertViewPointToCameraPoint(viewPoint: Offset): Point
}

/**
 * A reference to a [NativePreviewView], giving access to its methods (the equivalent of a `ref` on VisionCamera's
 * `<NativePreviewView />`). Create it with [rememberPreviewViewRef] and pass it to [NativePreviewView].
 *
 * View coordinates are in Compose pixels, as in pointer events (e.g. the offset of a tap).
 */
@Stable
class PreviewViewRef internal constructor() {
    internal var handle: PreviewViewHandle? = null

    /** Whether the [NativePreviewView] is displayed and its methods can be called. */
    val isReady: Boolean get() = handle != null

    private fun requireHandle(): PreviewViewHandle =
        handle ?: throw VisionCameraException("Camera Preview is not yet ready!")

    /**
     * Creates a [MeteringPoint] from a point of the view (e.g. a tap), taking orientation, scaling and cropping into
     * account, to focus with [me.nathanfallet.visioncamera.CameraController.focusTo].
     *
     * @throws VisionCameraException If the preview is not ready yet.
     */
    fun createMeteringPoint(viewPoint: Offset, size: Float? = null): MeteringPoint =
        requireHandle().createMeteringPoint(viewPoint, size)

    /**
     * Converts a point in camera sensor coordinates into a point of the view.
     *
     * @throws VisionCameraException If the preview is not ready yet.
     */
    fun convertCameraPointToViewPoint(cameraPoint: Point): Offset =
        requireHandle().convertCameraPointToViewPoint(cameraPoint)

    /**
     * Converts a point of the view into camera sensor coordinates.
     *
     * @throws VisionCameraException If the preview is not ready yet.
     */
    fun convertViewPointToCameraPoint(viewPoint: Offset): Point =
        requireHandle().convertViewPointToCameraPoint(viewPoint)
}

/**
 * Create a [PreviewViewRef], kept across recompositions.
 */
@Composable
fun rememberPreviewViewRef(): PreviewViewRef = remember { PreviewViewRef() }
