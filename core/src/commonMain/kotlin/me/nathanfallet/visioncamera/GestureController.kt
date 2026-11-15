package me.nathanfallet.visioncamera

/**
 * A [GestureController] controls a property of a [CameraController] with a user gesture, e.g. the zoom with a
 * pinch ([ZoomGestureController]).
 *
 * It has to be attached to a view (a `PreviewView`, through its `gestureControllers`) to enable the gesture.
 */
interface GestureController {
    /** The [CameraController] this gesture controls, or `null` to disable the gesture. */
    var controller: CameraController?
}

/**
 * A [GestureController] that calls [CameraController.focusTo] when the user taps the view.
 */
interface TapToFocusGestureController : GestureController {
    /**
     * Adds a listener called when a tap creates a [MeteringPoint], right before focusing begins.
     */
    fun addOnTapListener(onTap: (point: MeteringPoint) -> Unit): ListenerSubscription

    /**
     * Adds a listener called when the focus operation triggered by a tap completes. It is not called if the focus
     * operation fails.
     */
    fun addOnFocusCompletedListener(onFocusCompleted: (point: MeteringPoint) -> Unit): ListenerSubscription
}

/**
 * A [GestureController] that changes [CameraController.zoom] with a pinch gesture.
 */
interface ZoomGestureController : GestureController {
    /**
     * Adds a listener called whenever the pinch gesture changes the target zoom factor, possibly many times during
     * one gesture.
     */
    fun addOnZoomChangedListener(onZoomChanged: (zoom: Double) -> Unit): ListenerSubscription
}
