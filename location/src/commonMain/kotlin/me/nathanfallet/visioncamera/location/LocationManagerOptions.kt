package me.nathanfallet.visioncamera.location

/**
 * Configuration options for a [LocationManager].
 *
 * @see createLocationManager
 */
data class LocationManagerOptions(
    /** Configures the accuracy of location updates. */
    val accuracy: LocationAccuracy = LocationAccuracy.BALANCED,
    /**
     * Configures the distance the last location has to change in order to fire a new location update event, in
     * meters.
     */
    val distanceFilter: Double = 10.0,
    /**
     * Specifies the interval in which location updates should be requested from the system, in milliseconds.
     *
     * Only used on Android.
     */
    val updateInterval: Long = 10_000,
)
