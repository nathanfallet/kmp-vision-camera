package me.nathanfallet.visioncamera

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
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
 */
class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val cameraXPreviewView = CameraXPreviewView(context)
    private var isPreviewing = false
    private val streamStateObserver = Observer<CameraXPreviewView.StreamState> { onStreamStateChanged(it) }

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

    /** Called when the preview started streaming. */
    var onPreviewStarted: (() -> Unit)? = null

    /** Called when the preview stopped streaming. */
    var onPreviewStopped: (() -> Unit)? = null

    init {
        addView(cameraXPreviewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cameraXPreviewView.previewStreamState.observeForever(streamStateObserver)
    }

    override fun onDetachedFromWindow() {
        cameraXPreviewView.previewStreamState.removeObserver(streamStateObserver)
        super.onDetachedFromWindow()
    }

    private fun connectPreviewOutput(previewOutput: CameraPreviewOutput) {
        val output = previewOutput as? AndroidPreviewOutput
            ?: throw VisionCameraException("PreviewOutput was not created by VisionCamera!")
        output.setSurfaceProvider(cameraXPreviewView.surfaceProvider)
    }

    private fun disconnectPreviewOutput(previewOutput: CameraPreviewOutput) {
        (previewOutput as? AndroidPreviewOutput)?.removeSurfaceProvider(cameraXPreviewView.surfaceProvider)
    }

    private fun onStreamStateChanged(state: CameraXPreviewView.StreamState) {
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
}
