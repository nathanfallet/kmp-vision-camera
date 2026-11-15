package me.nathanfallet.visioncamera.location

import me.nathanfallet.visioncamera.ListenerSubscription
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus

/**
 * Streams device location updates and exposes the last known [Location].
 *
 * Create a [LocationManager] via [createLocationManager], or with `rememberLocationManager` in Compose. For an API that
 * handles permissions and subscriptions automatically in Compose, use `rememberLocation`. A [LocationManager] stops
 * updating when it is [closed][close].
 *
 * ```kotlin
 * val manager = createLocationManager(LocationManagerOptions(accuracy = LocationAccuracy.HIGH))
 * if (manager.requestLocationPermission()) {
 *     manager.addOnLocationChangedListener { location -> println("At ${location.latitude}, ${location.longitude}") }
 *     manager.startUpdating()
 * }
 * ```
 *
 * @see createLocationManager
 */
interface LocationManager : AutoCloseable {
    /** The last known [Location], or `null` if no location is known. */
    val lastKnownLocation: Location?

    /**
     * The current location permission status.
     *
     * The [PermissionStatus] depends on this [LocationManager]'s configuration, as properties like [LocationAccuracy]
     * may affect it.
     */
    val locationPermissionStatus: PermissionStatus

    /**
     * Requests the location permission.
     *
     * @return `true` if the permission has been granted.
     */
    suspend fun requestLocationPermission(): Boolean

    /**
     * Starts location updates.
     *
     * @throws me.nathanfallet.visioncamera.VisionCameraException if [locationPermissionStatus] is not
     * [PermissionStatus.AUTHORIZED].
     */
    suspend fun startUpdating()

    /** Stops location updates. */
    suspend fun stopUpdating()

    /**
     * Adds a [callback] to be called whenever the current [Location] changes.
     *
     * Call [ListenerSubscription.remove] to stop listening.
     */
    fun addOnLocationChangedListener(callback: (location: Location) -> Unit): ListenerSubscription
}
