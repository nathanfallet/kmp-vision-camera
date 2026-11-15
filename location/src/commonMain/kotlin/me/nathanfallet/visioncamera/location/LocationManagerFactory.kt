package me.nathanfallet.visioncamera.location

import me.nathanfallet.visioncamera.Location
import kotlin.time.Clock

/**
 * Creates a new [LocationManager] with the given [LocationManagerOptions].
 *
 * ```kotlin
 * val manager = createLocationManager(LocationManagerOptions(distanceFilter = 50.0))
 * ```
 */
fun createLocationManager(options: LocationManagerOptions = LocationManagerOptions()): LocationManager =
    createPlatformLocationManager(options)

/**
 * Creates a new fake [Location].
 *
 * You can use this API to generate a [Location] for use in EXIF data or video metadata, if you already have a known
 * [latitude] or [longitude] from a different API or a hardcoded fake location.
 */
fun createLocation(latitude: Double, longitude: Double): Location = Location(
    latitude = latitude,
    longitude = longitude,
    timestamp = Clock.System.now().toEpochMilliseconds().toDouble(),
)

/** Creates the [LocationManager] of the current platform. */
internal expect fun createPlatformLocationManager(options: LocationManagerOptions): LocationManager
