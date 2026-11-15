package me.nathanfallet.visioncamera.location.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus
import me.nathanfallet.visioncamera.location.LocationAccuracy
import me.nathanfallet.visioncamera.location.LocationManager

/**
 * The current state of [rememberLocation].
 */
@Stable
interface LocationState {
    /**
     * Whether the app has been granted permission to access the user's location.
     *
     * If this is `false`, call [requestPermission] to prompt the user.
     */
    val hasPermission: Boolean

    /**
     * The last known user [Location], or `null` if no location has been reported yet (e.g. because the permission has
     * not been granted, or because the device is still acquiring a fix).
     */
    val currentLocation: Location?

    /**
     * Requests the location permission from the user.
     *
     * @return whether the permission was granted after the request completed.
     */
    suspend fun requestPermission(): Boolean
}

/**
 * Reactively use the current user [Location] (the equivalent of VisionCamera's `useLocation`).
 *
 * Location updates start once the permission is granted and stop when this leaves the composition.
 *
 * ```kotlin
 * val location = rememberLocation()
 *
 * LaunchedEffect(location.hasPermission) {
 *     if (!location.hasPermission) location.requestPermission()
 * }
 *
 * location.currentLocation?.let { println("At ${it.latitude}, ${it.longitude}") }
 * ```
 *
 * @param accuracy The accuracy of location updates.
 * @param distanceFilter The distance the last location has to change in order to fire a new location update, in
 * meters.
 * @param updateInterval The interval in which location updates are requested from the system, in milliseconds
 * (Android only).
 */
@Composable
fun rememberLocation(
    accuracy: LocationAccuracy = LocationAccuracy.BALANCED,
    distanceFilter: Double = 10.0,
    updateInterval: Long = 10_000,
): LocationState = rememberLocation(rememberLocationManager(accuracy, distanceFilter, updateInterval))

/**
 * Reactively use the current user [Location] reported by the given [locationManager] (the equivalent of VisionCamera's
 * `useLocation`).
 *
 * Location updates start once the permission is granted and stop when this leaves the composition. The
 * [locationManager] is not closed.
 */
@Composable
fun rememberLocation(locationManager: LocationManager): LocationState {
    val state = remember(locationManager) { DefaultLocationState(locationManager) }

    DisposableEffect(locationManager) {
        val subscription = locationManager.addOnLocationChangedListener { state.currentLocation = it }
        onDispose { subscription.remove() }
    }

    LaunchedEffect(locationManager, state.hasPermission) {
        if (!state.hasPermission) return@LaunchedEffect
        try {
            locationManager.startUpdating()
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // The permission may have been revoked since: reflect it, and the effect restarts once it is granted.
            state.refreshPermission()
        } finally {
            withContext(NonCancellable) { locationManager.stopUpdating() }
        }
    }

    return state
}

private class DefaultLocationState(private val locationManager: LocationManager) : LocationState {
    override var hasPermission by mutableStateOf(isAuthorized())
        private set
    override var currentLocation by mutableStateOf(locationManager.lastKnownLocation)

    private fun isAuthorized() = locationManager.locationPermissionStatus == PermissionStatus.AUTHORIZED

    fun refreshPermission() {
        hasPermission = isAuthorized()
    }

    override suspend fun requestPermission(): Boolean {
        locationManager.requestLocationPermission()
        refreshPermission()
        return hasPermission
    }
}
