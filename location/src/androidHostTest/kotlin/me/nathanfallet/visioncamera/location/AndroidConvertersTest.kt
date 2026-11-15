package me.nathanfallet.visioncamera.location

import com.google.android.gms.location.Priority
import me.nathanfallet.visioncamera.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidConvertersTest {
    @Test
    fun mapsAccuracyToPriority() {
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, LocationAccuracy.HIGH.toPriority())
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LocationAccuracy.BALANCED.toPriority())
        assertEquals(Priority.PRIORITY_LOW_POWER, LocationAccuracy.LOW.toPriority())
    }

    @Test
    fun eitherAuthorizedPermissionIsAuthorized() {
        assertEquals(
            PermissionStatus.AUTHORIZED,
            combinePermissionStatuses(PermissionStatus.DENIED, PermissionStatus.AUTHORIZED),
        )
        assertEquals(
            PermissionStatus.AUTHORIZED,
            combinePermissionStatuses(PermissionStatus.AUTHORIZED, PermissionStatus.NOT_DETERMINED),
        )
    }

    @Test
    fun combinesUnauthorizedPermissionsByPriority() {
        assertEquals(
            PermissionStatus.NOT_DETERMINED,
            combinePermissionStatuses(PermissionStatus.DENIED, PermissionStatus.NOT_DETERMINED),
        )
        assertEquals(
            PermissionStatus.RESTRICTED,
            combinePermissionStatuses(PermissionStatus.RESTRICTED, PermissionStatus.DENIED),
        )
        assertEquals(
            PermissionStatus.DENIED,
            combinePermissionStatuses(PermissionStatus.DENIED, PermissionStatus.DENIED),
        )
    }
}
