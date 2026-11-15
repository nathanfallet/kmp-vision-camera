package me.nathanfallet.visioncamera

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.camera.view.PreviewView as CameraXPreviewView

/**
 * The Android [TapToFocusGestureController], calling [CameraController.focusTo] when the [PreviewView] is tapped.
 */
internal class AndroidTapToFocusGestureController : TapToFocusGestureController, NativeGestureController {
    override var controller: CameraController? = null

    private var previewView: CameraXPreviewView? = null
    private var isTracking = false
    private val onTapListeners = ListenerList<(MeteringPoint) -> Unit>()
    private val onFocusCompletedListeners = ListenerList<(MeteringPoint) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val gestureDetector = GestureDetector(
        AndroidContext.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val previewView = previewView
                val controller = controller
                if (previewView == null || controller == null) return false
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                val meteringPoint = AndroidMeteringPoint(e.x.toDouble(), e.y.toDouble(), null, point)
                onTapListeners.forEach { it(meteringPoint) }
                scope.launch { focus(controller, meteringPoint) }
                return true
            }
        },
        Handler(Looper.getMainLooper()),
    )

    private suspend fun focus(controller: CameraController, meteringPoint: MeteringPoint) {
        try {
            controller.focusTo(meteringPoint, FocusOptions())
            onFocusCompletedListeners.forEach { it(meteringPoint) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A failed focus (e.g. canceled by a newer tap) is not an error for the gesture.
            Log.e(TAG, "Failed to focus!", e)
        }
    }

    override fun onTouchEvent(view: View, event: MotionEvent): Boolean {
        // No controller available
        if (controller == null) return false
        val previewView = view as? CameraXPreviewView
        if (previewView != null) {
            this.previewView = previewView
        } else {
            Log.e(TAG, "Received a tap on a View that is not a PreviewView! Tap To Focus cannot handle this.")
        }
        return previewView != null && handleTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            // It's a multi-touch, ignore it
            isTracking = false
            return false
        }

        return when (event.actionMasked) {
            // Finger down
            MotionEvent.ACTION_DOWN -> {
                isTracking = true
                gestureDetector.onTouchEvent(event)
                true
            }
            // Finger up again
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureDetector.onTouchEvent(event)
                isTracking = false
                true
            }
            // Continue tracking this event, whatever it is
            else -> {
                if (isTracking) gestureDetector.onTouchEvent(event)
                isTracking
            }
        }
    }

    override fun addOnTapListener(onTap: (point: MeteringPoint) -> Unit): ListenerSubscription =
        onTapListeners.add(onTap)

    override fun addOnFocusCompletedListener(onFocusCompleted: (point: MeteringPoint) -> Unit): ListenerSubscription =
        onFocusCompletedListeners.add(onFocusCompleted)
}
