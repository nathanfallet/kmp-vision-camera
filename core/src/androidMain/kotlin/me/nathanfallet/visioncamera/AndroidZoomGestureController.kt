package me.nathanfallet.visioncamera

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.core.Camera

/**
 * The Android [ZoomGestureController], changing the zoom of a CameraX [Camera] with a [ScaleGestureDetector].
 */
internal class AndroidZoomGestureController : ZoomGestureController, NativeGestureController {
    override var controller: CameraController? = null

    private var initialZoomFactor = 1f
    private var isPinching = false
    private val onZoomChangedListeners = ListenerList<(Double) -> Unit>()
    private val camera: Camera? get() = (controller as? AndroidCameraController)?.camera

    private val gestureDetector = ScaleGestureDetector(
        AndroidContext.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val zoomState = camera?.cameraInfo?.zoomState?.value ?: return false
                initialZoomFactor = zoomState.zoomRatio
                isPinching = true
                return super.onScaleBegin(detector)
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val camera = camera
                val zoomState = camera?.cameraInfo?.zoomState?.value
                if (camera == null || zoomState == null) return false

                val targetFactor = initialZoomFactor * detector.scaleFactor
                val maxZoom = minOf(zoomState.maxZoomRatio, MAX_ZOOM_FACTOR)
                val zoom = targetFactor.coerceAtMost(maxZoom).coerceAtLeast(zoomState.minZoomRatio)

                onZoomChangedListeners.forEach { it(zoom.toDouble()) }
                camera.cameraControl.setZoomRatio(zoom)
                return super.onScale(detector)
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                super.onScaleEnd(detector)
            }
        },
        Handler(Looper.getMainLooper()),
    )

    override fun onTouchEvent(view: View, event: MotionEvent): Boolean {
        // No controller, we can ignore touches.
        if (controller == null) return false

        // Let the gesture detector pick up the touch event
        gestureDetector.onTouchEvent(event)

        // Decide whether we want to continue receiving touches or if we can free them up to the parent
        return when (event.actionMasked) {
            // Tap started: start receiving touches
            MotionEvent.ACTION_DOWN -> true
            // Multi-touch started: ask the parent not to intercept
            MotionEvent.ACTION_POINTER_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
            // Fingers moved: continue receiving touches if we are pinching, otherwise it might be a scroll.
            MotionEvent.ACTION_MOVE -> {
                val isMultiTouch = event.pointerCount > 1 || isPinching
                if (isMultiTouch) view.parent?.requestDisallowInterceptTouchEvent(true)
                isMultiTouch
            }
            // All fingers are released/canceled: stop pinching
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPinching = false
                false
            }
            // 1 finger released (keep receiving touches if we are still pinching), or anything else
            else -> event.pointerCount > 1 || isPinching
        }
    }

    override fun addOnZoomChangedListener(onZoomChanged: (zoom: Double) -> Unit): ListenerSubscription =
        onZoomChangedListeners.add(onZoomChanged)

    private companion object {
        const val MAX_ZOOM_FACTOR = 15f
    }
}
