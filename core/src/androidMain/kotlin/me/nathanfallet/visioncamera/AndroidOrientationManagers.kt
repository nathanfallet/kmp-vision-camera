package me.nathanfallet.visioncamera

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.OrientationEventListener

/**
 * The Android [OrientationManager] for [OrientationSource.DEVICE], backed by an [OrientationEventListener].
 */
internal class AndroidDeviceOrientationManager : OrientationManager {
    override val source: OrientationSource = OrientationSource.DEVICE

    @Volatile
    override var currentOrientation: CameraOrientation? = null
        private set

    private val context: Context
        get() = AndroidContext.context

    @Volatile
    private var orientationListener: OrientationEventListener? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use interface orientation as a default device orientation, it may change later.
            val activity = AndroidContext.currentActivity
            if (activity != null) {
                currentOrientation = cameraOrientationFromSurfaceRotation(activity.display.rotation)
            }
        }
    }

    override fun startOrientationUpdates(onChanged: (orientation: CameraOrientation) -> Unit) {
        orientationListener?.disable()
        currentOrientation?.let(onChanged)
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(rotationDegrees: Int) {
                if (orientationListener !== this) return
                if (rotationDegrees == ORIENTATION_UNKNOWN) {
                    // phone is laying flat - orientation is unknown! Avoid sending out event.
                    return
                }
                val orientation = cameraOrientationFromDegrees(rotationDegrees)
                if (currentOrientation != orientation) {
                    Log.i(TAG, "Device orientation changed! $orientation")
                    currentOrientation = orientation
                    onChanged(orientation)
                }
            }
        }
        orientationListener = listener
        listener.enable()
    }

    override fun stopOrientationUpdates() {
        val listener = orientationListener
        orientationListener = null
        listener?.disable()
    }
}

/**
 * The Android [OrientationManager] for [OrientationSource.INTERFACE], backed by a [DisplayManager] listener.
 */
internal class AndroidInterfaceOrientationManager : OrientationManager {
    override val source: OrientationSource = OrientationSource.INTERFACE

    @Volatile
    override var currentOrientation: CameraOrientation?
        private set

    private val displayManager = AndroidContext.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    @Volatile
    private var listener: DisplayManager.DisplayListener? = null

    init {
        currentOrientation = displayManager.displays.firstOrNull()
            ?.let { cameraOrientationFromSurfaceRotation(it.rotation) }
    }

    override fun startOrientationUpdates(onChanged: (orientation: CameraOrientation) -> Unit) {
        listener?.let(displayManager::unregisterDisplayListener)
        displayManager.displays.firstOrNull()?.let {
            currentOrientation = cameraOrientationFromSurfaceRotation(it.rotation)
        }
        currentOrientation?.let(onChanged)
        val newListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (listener !== this) return
                val display = displayManager.getDisplay(displayId) ?: return
                val orientation = cameraOrientationFromSurfaceRotation(display.rotation)
                if (currentOrientation != orientation) {
                    Log.i(TAG, "Display orientation changed! $orientation")
                    currentOrientation = orientation
                    onChanged(orientation)
                }
            }
        }
        listener = newListener
        displayManager.registerDisplayListener(newListener, null)
    }

    override fun stopOrientationUpdates() {
        val currentListener = listener
        listener = null
        currentListener?.let(displayManager::unregisterDisplayListener)
    }
}
