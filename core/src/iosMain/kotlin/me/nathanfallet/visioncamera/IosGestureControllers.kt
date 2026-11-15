@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.videoZoomFactor
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIPinchGestureRecognizer
import platform.UIKit.UITapGestureRecognizer
import platform.darwin.dispatch_async
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

// Port of V5's HybridTapToFocusGestureController.swift and HybridZoomGestureController.swift.

/**
 * The iOS [TapToFocusGestureController], focusing the [controller] where the user taps a [PreviewView].
 */
internal class IosTapToFocusGestureController : TapToFocusGestureController, NativeGestureController {
    override var controller: CameraController? = null

    private val tapListeners = ListenerList<(MeteringPoint) -> Unit>()
    private val focusCompletedListeners = ListenerList<(MeteringPoint) -> Unit>()
    private var previewView: WeakReference<PreviewView>? = null
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error ->
            Logger.error("Failed to focus! $error")
        },
    )
    private val target = GestureRecognizerTarget { onTap(it) }

    override val gestureRecognizer: UIGestureRecognizer = UITapGestureRecognizer().apply {
        cancelsTouchesInView = false
        target.attachTo(this)
    }

    private fun onTap(gestureRecognizer: UIGestureRecognizer) {
        val controller = controller
        val previewView = previewView?.get()
        if (controller == null || previewView == null) return
        val meteringPoint = createMeteringPoint(previewView, gestureRecognizer) ?: return
        tapListeners.values.forEach { it(meteringPoint) }
        scope.launch {
            controller.focusTo(meteringPoint, FocusOptions())
            focusCompletedListeners.values.forEach { it(meteringPoint) }
        }
    }

    private fun createMeteringPoint(previewView: PreviewView, gestureRecognizer: UIGestureRecognizer): MeteringPoint? =
        try {
            val viewPoint = gestureRecognizer.locationInView(gestureRecognizer.view)
            viewPoint.useContents { previewView.createMeteringPoint(viewX = x, viewY = y, size = null) }
        } catch (e: VisionCameraException) {
            Logger.error("Failed to focus! $e")
            null
        }

    override fun addOnTapListener(onTap: (point: MeteringPoint) -> Unit): ListenerSubscription =
        tapListeners.add(onTap)

    override fun addOnFocusCompletedListener(
        onFocusCompleted: (point: MeteringPoint) -> Unit,
    ): ListenerSubscription = focusCompletedListeners.add(onFocusCompleted)

    override fun onAttached(preview: PreviewView) {
        previewView = WeakReference(preview)
    }

    override fun onDetached(preview: PreviewView) {
        if (previewView?.get() === preview) previewView = null
    }
}

/**
 * The iOS [ZoomGestureController], zooming the [controller] when the user pinches a [PreviewView].
 */
internal class IosZoomGestureController : ZoomGestureController, NativeGestureController {
    override var controller: CameraController? = null

    private var initialZoomFactor = 1.0
    private val zoomChangedListeners = ListenerList<(Double) -> Unit>()
    private val target = GestureRecognizerTarget { onPinch(it as UIPinchGestureRecognizer) }

    override val gestureRecognizer: UIGestureRecognizer = UIPinchGestureRecognizer().apply {
        cancelsTouchesInView = false
        target.attachTo(this)
    }

    private fun onPinch(gestureRecognizer: UIPinchGestureRecognizer) {
        val controller = controller as? IosCameraController ?: return
        val device = controller.captureDevice
        when (gestureRecognizer.state) {
            UIGestureRecognizerStateBegan -> initialZoomFactor = device.videoZoomFactor
            UIGestureRecognizerStateChanged -> {
                val targetFactor = initialZoomFactor * gestureRecognizer.scale
                val minZoom = device.minAvailableVideoZoomFactor
                val maxZoom = minOf(device.maxAvailableVideoZoomFactor, MAX_ZOOM_FACTOR)
                val zoom = targetFactor.coerceAtLeast(minZoom).coerceAtMost(maxZoom)
                zoomChangedListeners.values.forEach { it(zoom) }
                dispatch_async(controller.queue) {
                    try {
                        device.lockForConfigurationOrThrow()
                        device.videoZoomFactor = zoom
                        device.unlockForConfiguration()
                    } catch (e: VisionCameraException) {
                        Logger.error("Failed to lock device for zooming! $e")
                    }
                }
            }
            else -> Unit
        }
    }

    override fun addOnZoomChangedListener(onZoomChanged: (zoom: Double) -> Unit): ListenerSubscription =
        zoomChangedListeners.add(onZoomChanged)

    private companion object {
        const val MAX_ZOOM_FACTOR = 15.0
    }
}

/**
 * A list of listeners, safe to modify while it is iterated (port of V5's `[UUID: Listener]` dictionaries). Only
 * used on the main thread.
 */
internal class ListenerList<T> {
    private val listeners = mutableMapOf<Long, T>()
    private var nextID = 0L

    /** A snapshot of all current listeners. */
    val values: List<T> get() = listeners.values.toList()

    /** Adds the given [listener], and returns a [ListenerSubscription] removing it. */
    fun add(listener: T): ListenerSubscription {
        val id = nextID++
        listeners[id] = listener
        return ListenerSubscription { listeners.remove(id) }
    }
}
