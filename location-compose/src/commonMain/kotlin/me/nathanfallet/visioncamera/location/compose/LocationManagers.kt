package me.nathanfallet.visioncamera.location.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.location.LocationAccuracy
import me.nathanfallet.visioncamera.location.LocationManager
import me.nathanfallet.visioncamera.location.LocationManagerOptions
import me.nathanfallet.visioncamera.location.createLocationManager

/**
 * Use a stable [LocationManager] with the given options (the equivalent of VisionCamera's `useLocationManager`).
 *
 * The [LocationManager] is kept across recompositions, recreated when an option changes, and closed (which stops its
 * location updates) once it leaves the composition.
 *
 * ```kotlin
 * val locationManager = rememberLocationManager(accuracy = LocationAccuracy.HIGH)
 * ```
 *
 * @param accuracy The accuracy of location updates.
 * @param distanceFilter The distance the last location has to change in order to fire a new location update, in
 * meters.
 * @param updateInterval The interval in which location updates are requested from the system, in milliseconds
 * (Android only).
 */
@Composable
fun rememberLocationManager(
    accuracy: LocationAccuracy = LocationAccuracy.BALANCED,
    distanceFilter: Double = 10.0,
    updateInterval: Long = 10_000,
): LocationManager = remember(accuracy, distanceFilter, updateInterval) {
    RememberedDisposable(
        createLocationManager(LocationManagerOptions(accuracy, distanceFilter, updateInterval)),
    ) { it.close() }
}.value
