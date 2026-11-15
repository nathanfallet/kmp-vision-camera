package me.nathanfallet.visioncamera.location

import kotlinx.cinterop.useContents
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationAccuracy
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.timeIntervalSince1970

private const val MILLISECONDS_PER_SECOND = 1_000.0

// Port of V5's CL+LocationAccuracy.swift and CL+PermissionStatus.swift.

/** Maps this [LocationAccuracy] to a `CLLocationAccuracy`. */
internal fun LocationAccuracy.toCLLocationAccuracy(): CLLocationAccuracy = when (this) {
    LocationAccuracy.HIGH -> kCLLocationAccuracyBest
    LocationAccuracy.BALANCED -> kCLLocationAccuracyNearestTenMeters
    LocationAccuracy.LOW -> kCLLocationAccuracyHundredMeters
}

/** Maps this `CLAuthorizationStatus` to a [PermissionStatus]. */
internal fun CLAuthorizationStatus.toPermissionStatus(): PermissionStatus = when (this) {
    kCLAuthorizationStatusNotDetermined -> PermissionStatus.NOT_DETERMINED
    kCLAuthorizationStatusRestricted -> PermissionStatus.RESTRICTED
    kCLAuthorizationStatusDenied -> PermissionStatus.DENIED
    kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.AUTHORIZED
    else -> error("Received unknown CLAuthorizationStatus! $this")
}

/** Converts this `CLLocation` to a [Location] (the timestamp being in milliseconds since the Unix epoch). */
internal fun CLLocation.toLocation(): Location = Location(
    latitude = coordinate.useContents { latitude },
    longitude = coordinate.useContents { longitude },
    altitude = altitude,
    horizontalAccuracy = horizontalAccuracy,
    verticalAccuracy = verticalAccuracy,
    timestamp = timestamp.timeIntervalSince1970 * MILLISECONDS_PER_SECOND,
    isMock = sourceInformation?.isSimulatedBySoftware ?: false,
)
