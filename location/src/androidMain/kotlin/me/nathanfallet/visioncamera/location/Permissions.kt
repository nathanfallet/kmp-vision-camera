package me.nathanfallet.visioncamera.location

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.nathanfallet.visioncamera.PermissionStatus
import me.nathanfallet.visioncamera.VisionCameraException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

// Same logic as the core module's (internal) Android permission helpers: Android can not tell "never requested" from
// "denied with don't ask again", so it is remembered in the SharedPreferences.

private const val PREFERENCES_NAME = "visioncamera.location.permissions"

private fun Context.preferences() = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun Context.hasRequested(permission: String) = preferences().getBoolean("$permission.requested", false)

private fun Context.isPermanentlyDenied(permission: String) =
    preferences().getBoolean("$permission.permanentlyDenied", false)

private fun Context.remember(permission: String, requested: Boolean, permanentlyDenied: Boolean) =
    preferences().edit {
        putBoolean("$permission.requested", requested)
        putBoolean("$permission.permanentlyDenied", permanentlyDenied)
    }

/** Gets the [PermissionStatus] of the given Android [permission]. */
internal fun Context.getPermissionStatus(permission: String): PermissionStatus {
    if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
        remember(permission, requested = false, permanentlyDenied = false)
        return PermissionStatus.AUTHORIZED
    }
    val activity = LocationAndroidContext.currentActivity
    return when {
        !hasRequested(permission) -> PermissionStatus.NOT_DETERMINED
        isPermanentlyDenied(permission) -> PermissionStatus.DENIED
        activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) ->
            PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }
}

private val permissionRequestCounter = AtomicInteger(0)

/**
 * Requests the given Android [permission] from the current activity, which must be a [ComponentActivity].
 *
 * @return `true` if the permission has been granted.
 */
internal suspend fun Context.requestPermission(permission: String): Boolean = withContext(Dispatchers.Main) {
    val activity = LocationAndroidContext.currentActivity as? ComponentActivity
        ?: throw VisionCameraException("Requesting a permission requires a resumed ComponentActivity!")
    val context = this@requestPermission
    remember(permission, requested = true, permanentlyDenied = false)
    suspendCancellableCoroutine { continuation ->
        val key = "visioncamera.location.permission.${permissionRequestCounter.incrementAndGet()}"
        var launcher: ActivityResultLauncher<String>? = null
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestPermission(),
        ) { hasPermission ->
            launcher?.unregister()
            if (hasPermission) {
                context.remember(permission, requested = false, permanentlyDenied = false)
            } else {
                val canRequestAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                context.remember(permission, requested = true, permanentlyDenied = !canRequestAgain)
            }
            if (continuation.isActive) continuation.resume(hasPermission)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        launcher.launch(permission)
    }
}
