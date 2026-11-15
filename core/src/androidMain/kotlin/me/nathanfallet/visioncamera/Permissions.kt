@file:Suppress("MatchingDeclarationName")

package me.nathanfallet.visioncamera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Remembers whether a permission has been requested, or permanently denied, since Android itself can not tell
 * "never requested" from "denied with don't ask again".
 */
internal object PermissionStateStore {
    private const val PREFERENCES_NAME = "visioncamera.permissions"

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasRequestedPermission(context: Context, permission: String): Boolean =
        preferences(context).getBoolean("$permission.requested", false)

    fun setHasRequestedPermission(context: Context, permission: String, hasRequestedPermission: Boolean) =
        preferences(context).edit { putBoolean("$permission.requested", hasRequestedPermission) }

    fun isPermissionPermanentlyDenied(context: Context, permission: String): Boolean =
        preferences(context).getBoolean("$permission.permanentlyDenied", false)

    fun setPermissionPermanentlyDenied(context: Context, permission: String, isPermanentlyDenied: Boolean) =
        preferences(context).edit { putBoolean("$permission.permanentlyDenied", isPermanentlyDenied) }
}

/**
 * Gets the [PermissionStatus] of the given Android [permission].
 */
internal fun Context.getPermissionStatus(permission: String): PermissionStatus =
    when (val status = ContextCompat.checkSelfPermission(this, permission)) {
        PackageManager.PERMISSION_GRANTED -> {
            PermissionStateStore.setHasRequestedPermission(this, permission, false)
            PermissionStateStore.setPermissionPermanentlyDenied(this, permission, false)
            PermissionStatus.AUTHORIZED
        }
        PackageManager.PERMISSION_DENIED -> getDeniedPermissionStatus(permission)
        else -> {
            Log.e(TAG, "Unknown Permission Status! $status")
            PermissionStatus.NOT_DETERMINED
        }
    }

private fun Context.getDeniedPermissionStatus(permission: String): PermissionStatus {
    val activity = AndroidContext.currentActivity
    return when {
        !PermissionStateStore.hasRequestedPermission(this, permission) -> PermissionStatus.NOT_DETERMINED
        PermissionStateStore.isPermissionPermanentlyDenied(this, permission) -> PermissionStatus.DENIED
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
    val activity = AndroidContext.currentActivity as? ComponentActivity
        ?: throw VisionCameraException("Requesting a permission requires a resumed ComponentActivity!")
    PermissionStateStore.setHasRequestedPermission(this@requestPermission, permission, true)
    suspendCancellableCoroutine { continuation ->
        val key = "visioncamera.permission.${permissionRequestCounter.incrementAndGet()}"
        var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestPermission(),
        ) { hasPermission ->
            launcher?.unregister()
            if (hasPermission) {
                PermissionStateStore.setHasRequestedPermission(this@requestPermission, permission, false)
                PermissionStateStore.setPermissionPermanentlyDenied(this@requestPermission, permission, false)
            } else {
                val canRequestAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                PermissionStateStore.setPermissionPermanentlyDenied(
                    this@requestPermission,
                    permission,
                    !canRequestAgain,
                )
            }
            if (continuation.isActive) continuation.resume(hasPermission)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        launcher.launch(permission)
    }
}

internal const val TAG = "VisionCamera"
