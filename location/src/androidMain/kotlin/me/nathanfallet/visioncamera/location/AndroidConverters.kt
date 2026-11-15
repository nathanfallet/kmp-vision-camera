package me.nathanfallet.visioncamera.location

import android.os.Build
import com.google.android.gms.location.Priority
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus

private const val MILLISECONDS_PER_SECOND = 1000.0

/** Maps this [LocationAccuracy] to a Play Services location request priority. */
internal fun LocationAccuracy.toPriority(): @Priority Int = when (this) {
    LocationAccuracy.LOW -> Priority.PRIORITY_LOW_POWER
    LocationAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    LocationAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
}

/**
 * Combines the statuses of the fine and coarse location permissions: authorized if either is, otherwise the "least
 * final" status (not determined, then restricted, then denied).
 */
internal fun combinePermissionStatuses(fine: PermissionStatus, coarse: PermissionStatus): PermissionStatus = when {
    fine == PermissionStatus.AUTHORIZED || coarse == PermissionStatus.AUTHORIZED -> PermissionStatus.AUTHORIZED
    fine == PermissionStatus.NOT_DETERMINED || coarse == PermissionStatus.NOT_DETERMINED ->
        PermissionStatus.NOT_DETERMINED
    fine == PermissionStatus.RESTRICTED || coarse == PermissionStatus.RESTRICTED -> PermissionStatus.RESTRICTED
    else -> PermissionStatus.DENIED
}

/** Converts this Android [android.location.Location] to a [Location] (the timestamp being in seconds). */
internal fun android.location.Location.toLocation(): Location = Location(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    horizontalAccuracy = accuracy.toDouble(),
    verticalAccuracy = verticalAccuracyMeters.toDouble(),
    timestamp = time / MILLISECONDS_PER_SECOND,
    isMock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isMock,
)
