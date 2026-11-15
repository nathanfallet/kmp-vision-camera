package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.nathanfallet.visioncamera.PermissionStatus
import me.nathanfallet.visioncamera.VisionCamera

/**
 * The state of a Camera or Microphone permission, as returned by [rememberCameraPermission] or
 * [rememberMicrophonePermission].
 */
@Stable
class PermissionState internal constructor(
    private val getStatus: () -> PermissionStatus,
    private val request: suspend () -> Boolean,
) {
    /** The current raw [PermissionStatus]. */
    var status: PermissionStatus by mutableStateOf(getStatus())
        private set

    /** Whether the app has been granted this permission ([status] is [PermissionStatus.AUTHORIZED]). */
    val hasPermission: Boolean get() = status == PermissionStatus.AUTHORIZED

    /**
     * Whether the app can still request this permission ([status] is [PermissionStatus.NOT_DETERMINED]). If this is
     * `false` but [hasPermission] is also `false`, the user must grant the permission from the system Settings.
     */
    val canRequestPermission: Boolean get() = status == PermissionStatus.NOT_DETERMINED

    /**
     * Requests the permission from the user. Can only be called if [canRequestPermission] is `true`.
     *
     * @return Whether the permission was granted.
     */
    suspend fun requestPermission(): Boolean {
        request()
        refresh()
        return hasPermission
    }

    internal fun refresh() {
        status = getStatus()
    }
}

/**
 * Use the Camera permission. The status is refreshed every time the app is resumed.
 *
 * ```kotlin
 * val permission = rememberCameraPermission()
 * LaunchedEffect(permission.hasPermission) {
 *     if (!permission.hasPermission) permission.requestPermission()
 * }
 * ```
 */
@Composable
fun rememberCameraPermission(): PermissionState = rememberPermission(
    getStatus = { VisionCamera.cameraPermissionStatus },
    request = { VisionCamera.requestCameraPermission() },
)

/**
 * Use the Microphone permission. The status is refreshed every time the app is resumed.
 */
@Composable
fun rememberMicrophonePermission(): PermissionState = rememberPermission(
    getStatus = { VisionCamera.microphonePermissionStatus },
    request = { VisionCamera.requestMicrophonePermission() },
)

@Composable
private fun rememberPermission(
    getStatus: () -> PermissionStatus,
    request: suspend () -> Boolean,
): PermissionState {
    val state = remember { PermissionState(getStatus, request) }
    // Re-fetch the permission when the app comes back, e.g. from the Settings.
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }
    return state
}
