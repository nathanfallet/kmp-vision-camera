package me.nathanfallet.visioncamera

/**
 * A geographic location, embedded in the metadata of a [Photo] ([CapturePhotoSettings.location]) or a video
 * ([RecorderSettings.location]).
 */
data class Location(
    /** The latitude, in degrees. */
    val latitude: Double,
    /** The longitude, in degrees. */
    val longitude: Double,
    /** The altitude, in meters. */
    val altitude: Double = 0.0,
    /** The horizontal accuracy, in meters. */
    val horizontalAccuracy: Double = 0.0,
    /** The vertical accuracy, in meters. */
    val verticalAccuracy: Double = 0.0,
    /** The time the location was measured at, in seconds since the Unix epoch. */
    val timestamp: Double = 0.0,
    /** Whether the location comes from a mock provider. */
    val isMock: Boolean = false,
)
