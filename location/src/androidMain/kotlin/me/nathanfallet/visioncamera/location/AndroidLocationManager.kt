package me.nathanfallet.visioncamera.location

import android.Manifest
import android.annotation.SuppressLint
import android.os.HandlerThread
import android.util.Log
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import me.nathanfallet.visioncamera.ListenerSubscription
import me.nathanfallet.visioncamera.Location
import me.nathanfallet.visioncamera.PermissionStatus
import me.nathanfallet.visioncamera.VisionCameraException
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "VisionCameraLocation"

/**
 * The Android [LocationManager], backed by Google Play Services' `FusedLocationProviderClient`.
 *
 * Port of V5's HybridLocationManager.kt.
 */
internal class AndroidLocationManager(private val options: LocationManagerOptions) : LocationManager {
    private val context
        get() = LocationAndroidContext.context
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val thread = HandlerThread("VisionCameraLocation").apply { start() }
    private val listeners = CopyOnWriteArrayList<(Location) -> Unit>()

    @Volatile
    override var lastKnownLocation: Location? = null
        private set

    override val locationPermissionStatus: PermissionStatus
        get() = combinePermissionStatuses(
            fine = context.getPermissionStatus(Manifest.permission.ACCESS_FINE_LOCATION),
            coarse = context.getPermissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION),
        )

    private val locationListener = LocationListener { androidLocation ->
        val location = androidLocation.toLocation()
        lastKnownLocation = location
        for (listener in listeners) listener(location)
    }

    override suspend fun requestLocationPermission(): Boolean {
        val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        for (permission in permissions) {
            val canRequest = locationPermissionStatus != PermissionStatus.AUTHORIZED &&
                context.getPermissionStatus(permission) == PermissionStatus.NOT_DETERMINED
            if (!canRequest) continue
            try {
                context.requestPermission(permission)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Failed to request $permission permission!", e)
            }
        }
        return locationPermissionStatus == PermissionStatus.AUTHORIZED
    }

    @SuppressLint("MissingPermission")
    override suspend fun startUpdating() {
        if (locationPermissionStatus != PermissionStatus.AUTHORIZED) {
            throw VisionCameraException("Cannot start location updates - the location permission is not granted!")
        }
        val request = LocationRequest.Builder(options.accuracy.toPriority(), options.updateInterval)
            .setMinUpdateDistanceMeters(options.distanceFilter.toFloat())
            .build()
        client.requestLocationUpdates(request, locationListener, thread.looper).await()
    }

    override suspend fun stopUpdating() {
        client.removeLocationUpdates(locationListener).await()
    }

    override fun addOnLocationChangedListener(callback: (location: Location) -> Unit): ListenerSubscription {
        listeners.add(callback)
        return ListenerSubscription { listeners.remove(callback) }
    }

    override fun close() {
        client.removeLocationUpdates(locationListener)
        listeners.clear()
        thread.quitSafely()
    }
}

internal actual fun createPlatformLocationManager(options: LocationManagerOptions): LocationManager =
    AndroidLocationManager(options)
