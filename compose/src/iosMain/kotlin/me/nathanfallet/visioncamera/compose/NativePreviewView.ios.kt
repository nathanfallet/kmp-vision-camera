package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.UIKitView
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.GestureController
import me.nathanfallet.visioncamera.MeteringPoint
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.PreviewImplementationMode
import me.nathanfallet.visioncamera.PreviewResizeMode
import me.nathanfallet.visioncamera.PreviewView
import me.nathanfallet.visioncamera.ScannedObject

/** Displays the Camera feed in an AVFoundation-backed iOS [PreviewView], see the common declaration. */
@Suppress("LongParameterList", "UnusedParameter")
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
    UIKitView(
        factory = { PreviewView().also { view = it } },
        modifier = modifier,
        update = { view ->
            if (view.previewOutput !== previewOutput) view.previewOutput = previewOutput
            view.resizeMode = resizeMode
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
    val density = LocalDensity.current.density
    DisposableEffect(ref, currentView, density) {
        if (ref != null && currentView != null) ref.handle = IosPreviewViewHandle(currentView, density)
        onDispose { ref?.handle = null }
    }
}

/** UIKit views use points, while Compose uses pixels: convert with the screen [density]. */
private class IosPreviewViewHandle(private val view: PreviewView, private val density: Float) : PreviewViewHandle {
    override fun createMeteringPoint(viewPoint: Offset, size: Float?): MeteringPoint = view.createMeteringPoint(
        (viewPoint.x / density).toDouble(),
        (viewPoint.y / density).toDouble(),
        size?.let { (it / density).toDouble() },
    )

    override fun convertCameraPointToViewPoint(cameraPoint: Point): Offset =
        view.convertCameraPointToViewPoint(cameraPoint).let {
            Offset(it.x.toFloat() * density, it.y.toFloat() * density)
        }

    override fun convertViewPointToCameraPoint(viewPoint: Offset): Point = view.convertViewPointToCameraPoint(
        Point((viewPoint.x / density).toDouble(), (viewPoint.y / density).toDouble()),
    )

    override fun convertScannedObjectCoordinatesToViewCoordinates(scannedObject: ScannedObject): ScannedObject =
        view.convertScannedObjectCoordinatesToViewCoordinates(scannedObject).scaled(density.toDouble())
}
