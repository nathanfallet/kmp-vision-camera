package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.GestureController
import me.nathanfallet.visioncamera.Image
import me.nathanfallet.visioncamera.MeteringPoint
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.PreviewImplementationMode
import me.nathanfallet.visioncamera.PreviewResizeMode
import me.nathanfallet.visioncamera.PreviewView
import me.nathanfallet.visioncamera.ScannedObject

/** Displays the Camera feed in a CameraX-backed Android [PreviewView], see the common declaration. */
@Suppress("LongParameterList")
@Composable
actual fun NativePreviewView(
    previewOutput: CameraPreviewOutput?,
    modifier: Modifier,
    resizeMode: PreviewResizeMode,
    implementationMode: PreviewImplementationMode,
    gestureControllers: List<GestureController>,
    ref: PreviewViewRef?,
    onPreviewStarted: (() -> Unit)?,
    onPreviewStopped: (() -> Unit)?,
) {
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        factory = { context -> PreviewView(context).also { view = it } },
        modifier = modifier,
        update = { view ->
            if (view.previewOutput !== previewOutput) view.previewOutput = previewOutput
            view.resizeMode = resizeMode
            view.implementationMode = implementationMode
            if (view.gestureControllers != gestureControllers) view.gestureControllers = gestureControllers
            view.onPreviewStarted = onPreviewStarted
            view.onPreviewStopped = onPreviewStopped
        },
        onRelease = { view ->
            view.gestureControllers = emptyList()
            view.previewOutput = null
        },
    )
    val currentView = view
    DisposableEffect(ref, currentView) {
        if (ref != null && currentView != null) ref.handle = AndroidPreviewViewHandle(currentView)
        onDispose { ref?.handle = null }
    }
}

/** Android views use pixels, like Compose: no conversion is needed. */
private class AndroidPreviewViewHandle(private val view: PreviewView) : PreviewViewHandle {
    override fun createMeteringPoint(viewPoint: Offset, size: Float?): MeteringPoint =
        view.createMeteringPoint(viewPoint.x.toDouble(), viewPoint.y.toDouble(), size?.toDouble())

    override fun convertCameraPointToViewPoint(cameraPoint: Point): Offset =
        view.convertCameraPointToViewPoint(cameraPoint).let { Offset(it.x.toFloat(), it.y.toFloat()) }

    override fun convertViewPointToCameraPoint(viewPoint: Offset): Point =
        view.convertViewPointToCameraPoint(Point(viewPoint.x.toDouble(), viewPoint.y.toDouble()))

    override fun convertScannedObjectCoordinatesToViewCoordinates(scannedObject: ScannedObject): ScannedObject =
        view.convertScannedObjectCoordinatesToViewCoordinates(scannedObject)

    override suspend fun takeSnapshot(): Image = view.takeSnapshot()
}
