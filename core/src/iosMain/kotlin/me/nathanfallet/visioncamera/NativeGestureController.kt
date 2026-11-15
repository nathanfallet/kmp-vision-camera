package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.darwin.NSObject

/**
 * A [GestureController] backed by a [UIGestureRecognizer], installed by a [PreviewView] (port of V5's
 * `Public/NativeGestureController.swift`).
 */
internal interface NativeGestureController : GestureController {
    /** The underlying [UIGestureRecognizer]. */
    val gestureRecognizer: UIGestureRecognizer

    /** Called when this gesture is attached to the given [preview]. */
    fun onAttached(preview: PreviewView) = Unit

    /** Called when this gesture is detached from the given [preview]. */
    fun onDetached(preview: PreviewView) = Unit
}

/**
 * The Objective-C target of a [UIGestureRecognizer], forwarding its action to [onAction].
 *
 * A [UIGestureRecognizer] does not retain its targets: the owner must keep a reference to this target.
 */
@OptIn(BetaInteropApi::class)
internal class GestureRecognizerTarget(
    private val onAction: (UIGestureRecognizer) -> Unit,
) : NSObject() {
    @ObjCAction
    fun handleGesture(sender: UIGestureRecognizer) {
        onAction(sender)
    }

    /** Registers this target on the given [gestureRecognizer]. */
    fun attachTo(gestureRecognizer: UIGestureRecognizer) {
        gestureRecognizer.addTarget(this, action = NSSelectorFromString("handleGesture:"))
    }
}
