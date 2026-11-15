package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.camera.view.TransformExperimental
import androidx.lifecycle.Observer
import androidx.camera.view.PreviewView as CameraXPreviewView

/**
 * An Android [android.view.View] displaying the Camera feed of a [CameraPreviewOutput].
 *
 * It can be used directly in an Android View hierarchy, or through the `CameraPreview` composable of the compose
 * module.
 *
 * ```kotlin
 * val previewView = PreviewView(context)
 * previewView.previewOutput = previewOutput
 * ```
 *
 * View coordinates ([createMeteringPoint], [convertCameraPointToViewPoint], [convertViewPointToCameraPoint]) are in
 * pixels, relative to this view, like the coordinates of a [MotionEvent].
 */
class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val cameraXPreviewView = CameraXPreviewView(context)
    private var isPreviewing = false
    private val streamStateObserver = Observer<CameraXPreviewView.StreamState> { onStreamStateChanged(it) }

    /**
     * Maps CameraX sensor coordinates to view pixels. It has to be updated when the layout changes, since
     * `sensorToViewTransform` is only accessible from the main thread.
     */
    @Volatile
    private var cameraSpaceToViewPixelsMatrix: Matrix? = null

    /**
     * The [CameraPreviewOutput] to display.
     */
    var previewOutput: CameraPreviewOutput? = null
        set(value) {
            field?.let { disconnectPreviewOutput(it) }
            field = value
            value?.let { connectPreviewOutput(it) }
        }

    /**
     * How the Camera feed is scaled into this view's bounds.
     */
    var resizeMode: PreviewResizeMode = PreviewResizeMode.COVER
        set(value) {
            field = value
            cameraXPreviewView.scaleType = when (value) {
                PreviewResizeMode.COVER -> CameraXPreviewView.ScaleType.FILL_CENTER
                PreviewResizeMode.CONTAIN -> CameraXPreviewView.ScaleType.FIT_CENTER
            }
        }

    /**
     * Whether the preview uses a `SurfaceView` ([PreviewImplementationMode.PERFORMANCE]) or a `TextureView`
     * ([PreviewImplementationMode.COMPATIBLE]).
     */
    var implementationMode: PreviewImplementationMode = PreviewImplementationMode.PERFORMANCE
        set(value) {
            if (field == value) return
            field = value
            cameraXPreviewView.implementationMode = when (value) {
                PreviewImplementationMode.PERFORMANCE -> CameraXPreviewView.ImplementationMode.PERFORMANCE
                PreviewImplementationMode.COMPATIBLE -> CameraXPreviewView.ImplementationMode.COMPATIBLE
            }
            // Reconnect the output so it streams into the new surface.
            previewOutput?.let { connectPreviewOutput(it) }
        }

    /**
     * The [GestureController]s installed on this view, e.g. a [ZoomGestureController] for a native pinch-to-zoom
     * gesture, or a [TapToFocusGestureController].
     */
    var gestureControllers: List<GestureController> = emptyList()

    /** Called when the preview started streaming. */
    var onPreviewStarted: (() -> Unit)? = null

    /** Called when the preview stopped streaming. */
    var onPreviewStopped: (() -> Unit)? = null

    init {
        addView(cameraXPreviewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cameraXPreviewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateCameraSpaceToViewPixelsMatrix()
        }
        installGestureHandler()
    }

    @SuppressLint("ClickableViewAccessibility") // Gestures are optional helpers, not the only way to act.
    private fun installGestureHandler() {
        cameraXPreviewView.setOnTouchListener { view, event -> onTouch(view, event) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cameraXPreviewView.previewStreamState.observeForever(streamStateObserver)
    }

    override fun onDetachedFromWindow() {
        cameraXPreviewView.previewStreamState.removeObserver(streamStateObserver)
        super.onDetachedFromWindow()
    }

    /**
     * Creates a [MeteringPoint] to focus AE/AF/AWB on the Camera via [CameraController.focusTo].
     *
     * The coordinates ([viewX], [viewY]) are relative to this view, in pixels (e.g. the `x` and `y` of a tap
     * [MotionEvent]), and orientation, scaling and cropping are taken into account.
     *
     * @param viewX The X coordinate within this view.
     * @param viewY The Y coordinate within this view.
     * @param size The size of the point, normalized from `0.0` to `1.0`, or `null` for the default size.
     */
    fun createMeteringPoint(viewX: Double, viewY: Double, size: Double? = null): MeteringPoint {
        val factory = cameraXPreviewView.meteringPointFactory
        val meteringPoint = if (size != null) {
            factory.createPoint(viewX.toFloat(), viewY.toFloat(), size.toFloat())
        } else {
            factory.createPoint(viewX.toFloat(), viewY.toFloat())
        }
        return AndroidMeteringPoint(viewX, viewY, size, meteringPoint)
    }

    /**
     * Converts the given [cameraPoint] in Camera sensor coordinates into a [Point] in view coordinates (pixels),
     * relative to this view.
     *
     * Camera sensor coordinates are not necessarily normalized from `0.0` to `1.0`.
     *
     * @throws VisionCameraException If this view isn't ready yet.
     */
    fun convertCameraPointToViewPoint(cameraPoint: Point): Point {
        val matrix = cameraSpaceToViewPixelsMatrix
            ?: throw VisionCameraException("Cannot convert camera point to view point - PreviewView isn't ready yet!")
        return matrix.convertPoint(cameraPoint)
    }

    /**
     * Converts the given [viewPoint] in view coordinates (pixels), relative to this view, into a [Point] in Camera
     * sensor coordinates.
     *
     * @throws VisionCameraException If this view isn't ready yet.
     */
    fun convertViewPointToCameraPoint(viewPoint: Point): Point {
        val matrix = cameraSpaceToViewPixelsMatrix
            ?: throw VisionCameraException("Cannot convert view point to camera point - PreviewView isn't ready yet!")
        val inverted = Matrix()
        matrix.invert(inverted)
        return inverted.convertPoint(viewPoint)
    }

    /**
     * Converts the coordinates of a [ScannedObject] into view coordinates. Not supported on Android, like V5:
     * [ScannedObject]s are only produced on iOS.
     *
     * @throws VisionCameraException Always.
     */
    @Suppress("UnusedParameter")
    fun convertScannedObjectCoordinatesToViewCoordinates(scannedObject: ScannedObject): ScannedObject =
        throw VisionCameraException("Transforming Scanned Objects into View Coordinates is not supported on Android!")

    private fun connectPreviewOutput(previewOutput: CameraPreviewOutput) {
        val output = previewOutput as? AndroidPreviewOutput
            ?: throw VisionCameraException("PreviewOutput was not created by VisionCamera!")
        output.setSurfaceProvider(cameraXPreviewView.surfaceProvider)
    }

    private fun disconnectPreviewOutput(previewOutput: CameraPreviewOutput) {
        (previewOutput as? AndroidPreviewOutput)?.removeSurfaceProvider(cameraXPreviewView.surfaceProvider)
    }

    private fun onStreamStateChanged(state: CameraXPreviewView.StreamState) {
        updateCameraSpaceToViewPixelsMatrix()
        val isNowPreviewing = state == CameraXPreviewView.StreamState.STREAMING
        if (isPreviewing == isNowPreviewing) return
        isPreviewing = isNowPreviewing
        if (isNowPreviewing) {
            Log.i(TAG, "PreviewView started!")
            onPreviewStarted?.invoke()
        } else {
            Log.i(TAG, "PreviewView stopped!")
            onPreviewStopped?.invoke()
        }
    }

    @androidx.annotation.OptIn(TransformExperimental::class)
    private fun updateCameraSpaceToViewPixelsMatrix() {
        val matrix = cameraXPreviewView.sensorToViewTransform ?: return
        cameraSpaceToViewPixelsMatrix = matrix
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean =
        // The first gesture that wants to keep receiving touches handles them.
        gestureControllers.any { controller ->
            controller is NativeGestureController && controller.onTouchEvent(view, event)
        }
}
