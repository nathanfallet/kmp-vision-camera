package me.nathanfallet.visioncamera

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationUnknown
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * Tracks the interface orientation of the app's main window scene (port of V5's
 * `HybridInterfaceOrientationManager.swift`). All work happens on the main thread.
 */
internal class InterfaceOrientationManager {
    /** The last known interface orientation, or `null` if it is not known yet. */
    var currentOrientation: CameraOrientation? = null
        private set

    private var observer: NSObjectProtocol? = null

    init {
        runOnMainThread {
            currentOrientation = cameraOrientationOfInterface(currentInterfaceOrientation())
        }
    }

    /**
     * Starts listening to interface orientation changes, calling [onChanged] on the main thread with the current
     * orientation, and every time it changes.
     */
    fun startOrientationUpdates(onChanged: (CameraOrientation) -> Unit) = runOnMainThread {
        Logger.info("Starting interface orientation updates...")
        val center = NSNotificationCenter.defaultCenter
        observer?.let {
            // Remove old listeners
            center.removeObserver(it)
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }

        // Start new listener (beginGeneratingDeviceOrientationNotifications() can be nested)
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()

        val interfaceOrientation = currentInterfaceOrientation()
        if (interfaceOrientation != UIInterfaceOrientationUnknown) {
            val orientation = cameraOrientationOfInterface(interfaceOrientation)
            currentOrientation = orientation
            onChanged(orientation)
        }

        observer = center.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val newInterfaceOrientation = currentInterfaceOrientation()
            if (newInterfaceOrientation == UIInterfaceOrientationUnknown) {
                Logger.info("UIInterfaceOrientation is unknown!")
                return@addObserverForName
            }
            val orientation = cameraOrientationOfInterface(newInterfaceOrientation)
            if (currentOrientation != orientation) {
                Logger.info("Interface orientation changed: $orientation")
                currentOrientation = orientation
                onChanged(orientation)
            }
        }
    }

    /** Stops listening to interface orientation changes. */
    fun stopOrientationUpdates() = runOnMainThread {
        observer?.let {
            Logger.info("Stopping interface orientation updates...")
            NSNotificationCenter.defaultCenter.removeObserver(it)
            observer = null
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }
    }

    /** Gets the interface orientation of the main (foreground active) window scene of the application. */
    private fun currentInterfaceOrientation(): UIInterfaceOrientation {
        val windowScene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?: return UIInterfaceOrientationUnknown
        return windowScene.interfaceOrientation
    }
}
