package me.nathanfallet.visioncamera

import android.view.MotionEvent
import android.view.View

/**
 * A [GestureController] handling the touch events of the [PreviewView] it is attached to.
 */
internal interface NativeGestureController : GestureController {
    /**
     * Handles the given [event] received by [view]. Returns `true` if touches should continue to be received.
     */
    fun onTouchEvent(view: View, event: MotionEvent): Boolean
}
