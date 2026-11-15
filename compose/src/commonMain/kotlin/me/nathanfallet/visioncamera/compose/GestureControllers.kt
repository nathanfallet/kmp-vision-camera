package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.GestureController
import me.nathanfallet.visioncamera.VisionCamera

/**
 * Create the native [GestureController]s to attach to a [NativePreviewView], and keep them bound to the given
 * [controller]. [Camera] does it for you with `enableNativeTapToFocusGesture` and `enableNativeZoomGesture`.
 *
 * @param controller The [CameraController] the gestures control, or `null` to disable them.
 * @param enableNativeTapToFocusGesture Whether to install the native tap-to-focus gesture.
 * @param enableNativeZoomGesture Whether to install the native pinch-to-zoom gesture.
 */
@Composable
fun rememberGestureControllers(
    controller: CameraController?,
    enableNativeTapToFocusGesture: Boolean,
    enableNativeZoomGesture: Boolean,
): List<GestureController> {
    val gestureControllers = remember(enableNativeTapToFocusGesture, enableNativeZoomGesture) {
        buildList {
            // Single tap gestures need to come before any multi-touch gestures.
            if (enableNativeTapToFocusGesture) add(VisionCamera.createTapToFocusGestureController())
            if (enableNativeZoomGesture) add(VisionCamera.createZoomGestureController())
        }
    }
    SideEffect {
        gestureControllers.forEach { it.controller = controller }
    }
    return gestureControllers
}
