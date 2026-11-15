package me.nathanfallet.visioncamera.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.nathanfallet.visioncamera.ListenerSubscription
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus
import me.nathanfallet.visioncamera.VisionCameraException
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSBundle
import kotlin.coroutines.resume

/**
 * The iOS [LocationManager], backed by a CoreLocation `CLLocationManager`.
 *
 * Create it on the main thread (like any `CLLocationManager`), as CoreLocation delivers its events to the run loop of
 * the thread that created the manager. Port of V5's HybridLocationManager.swift.
 */
internal class IosLocationManager(options: LocationManagerOptions) : LocationManager {
    private val manager = CLLocationManager()
    private val delegate = LocationDelegate()

    override val lastKnownLocation: Location?
        get() = manager.location?.toLocation()

    override val locationPermissionStatus: PermissionStatus
        get() = manager.authorizationStatus.toPermissionStatus()

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = options.accuracy.toCLLocationAccuracy()
        manager.distanceFilter = options.distanceFilter
        manager.pausesLocationUpdatesAutomatically = true
    }

    override suspend fun requestLocationPermission(): Boolean {
        // `requestWhenInUseAuthorization` only prompts when the status is not determined.
        if (locationPermissionStatus != PermissionStatus.NOT_DETERMINED) {
            return locationPermissionStatus == PermissionStatus.AUTHORIZED
        }
        if (NSBundle.mainBundle.objectForInfoDictionaryKey("NSLocationWhenInUseUsageDescription") == null) {
            throw VisionCameraException(
                "Cannot request Location Permission - Your Info.plist does not contain a " +
                    "`NSLocationWhenInUseUsageDescription` key!",
            )
        }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                lateinit var subscription: ListenerSubscription
                subscription = delegate.addOnAuthorizationChangedListener {
                    // The delegate is also notified while the status is still undetermined: wait for the answer.
                    if (locationPermissionStatus != PermissionStatus.NOT_DETERMINED) {
                        subscription.remove()
                        if (continuation.isActive) {
                            continuation.resume(locationPermissionStatus == PermissionStatus.AUTHORIZED)
                        }
                    }
                }
                continuation.invokeOnCancellation { subscription.remove() }
                manager.requestWhenInUseAuthorization()
            }
        }
    }

    override suspend fun startUpdating() {
        if (locationPermissionStatus != PermissionStatus.AUTHORIZED) {
            throw VisionCameraException("Cannot start location updates - the location permission is not granted!")
        }
        withContext(Dispatchers.Main) { manager.startUpdatingLocation() }
    }

    override suspend fun stopUpdating() {
        withContext(Dispatchers.Main) { manager.stopUpdatingLocation() }
    }

    override fun addOnLocationChangedListener(callback: (location: Location) -> Unit): ListenerSubscription =
        delegate.addOnLocationUpdatedListener { callback(it.toLocation()) }

    override fun close() {
        manager.stopUpdatingLocation()
        manager.delegate = null
    }
}

internal actual fun createPlatformLocationManager(options: LocationManagerOptions): LocationManager =
    IosLocationManager(options)
