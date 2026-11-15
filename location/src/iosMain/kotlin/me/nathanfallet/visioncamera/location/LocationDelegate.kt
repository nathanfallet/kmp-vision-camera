package me.nathanfallet.visioncamera.location

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCSignatureOverride
import me.nathanfallet.visioncamera.ListenerSubscription
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * The delegate of the `CLLocationManager` of an [IosLocationManager], dispatching its events to listeners.
 *
 * Port of V5's LocationDelegate.swift.
 */
@OptIn(BetaInteropApi::class)
internal class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    private val onLocationUpdatedListeners = mutableListOf<(CLLocation) -> Unit>()
    private val onAuthorizationChangedListeners = mutableListOf<() -> Unit>()

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        for (listener in onLocationUpdatedListeners.toList()) listener(location)
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // TODO(V5): provide onError callbacks, like V5.
        println("VisionCameraLocation: error: ${didFailWithError.localizedDescription}")
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        for (listener in onAuthorizationChangedListeners.toList()) listener()
    }

    /** Adds a [callback] called with the latest `CLLocation` of every update. */
    fun addOnLocationUpdatedListener(callback: (CLLocation) -> Unit): ListenerSubscription {
        onLocationUpdatedListeners.add(callback)
        return ListenerSubscription { onLocationUpdatedListeners.remove(callback) }
    }

    /** Adds a [callback] called whenever the location authorization changes. */
    fun addOnAuthorizationChangedListener(callback: () -> Unit): ListenerSubscription {
        onAuthorizationChangedListeners.add(callback)
        return ListenerSubscription { onAuthorizationChangedListeners.remove(callback) }
    }
}
