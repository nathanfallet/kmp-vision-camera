@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.useContents
import platform.CoreMotion.CMAccelerometerData
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.abs
import kotlin.native.ref.WeakReference

/**
 * The [OrientationManager] tracking the physical device orientation with the accelerometer, even if screen rotation is
 * locked (port of V5's `HybridDeviceOrientationManager.swift`).
 */
internal class DeviceOrientationManager : OrientationManager {
    override val source: OrientationSource = OrientationSource.DEVICE

    @Volatile
    override var currentOrientation: CameraOrientation? = null
        private set

    private val motionManager = CMMotionManager()
    private val operationQueue = NSOperationQueue()

    init {
        motionManager.accelerometerUpdateInterval = ACCELEROMETER_UPDATE_INTERVAL
    }

    /**
     * Starts listening to device orientation changes, calling [onChanged] on a background queue.
     *
     * @throws VisionCameraException If the accelerometer is not available.
     */
    override fun startOrientationUpdates(onChanged: (orientation: CameraOrientation) -> Unit) {
        Logger.info("Starting device orientation updates...")
        if (!motionManager.accelerometerAvailable) {
            throw VisionCameraException(
                "Cannot listen to device orientation changes - accelerometer is not available!",
            )
        }
        if (motionManager.accelerometerActive) {
            motionManager.stopAccelerometerUpdates()
        }
        currentOrientation?.let(onChanged)

        // The handler only holds a weak reference to this manager, so the motion manager does not keep it alive.
        val weakThis = WeakReference(this)
        motionManager.startAccelerometerUpdatesToQueue(operationQueue) { accelerometerData, error ->
            val manager = weakThis.value ?: return@startAccelerometerUpdatesToQueue
            if (error != null) {
                Logger.error("Failed to get Accelerometer data! ${error.localizedDescription}")
            }
            val orientation = accelerometerData?.deviceOrientation ?: return@startAccelerometerUpdatesToQueue
            if (manager.currentOrientation != orientation) {
                Logger.info("Device orientation changed: $orientation")
                manager.currentOrientation = orientation
                onChanged(orientation)
            }
        }
    }

    override fun stopOrientationUpdates() {
        Logger.info("Stopping device orientation updates...")
        if (motionManager.accelerometerActive) {
            motionManager.stopAccelerometerUpdates()
        }
    }

    private companion object {
        /** 200ms. */
        const val ACCELEROMETER_UPDATE_INTERVAL = 0.2
    }
}

/**
 * The current device orientation from the given acceleration/gyro data, or `null` if it is unknown (port of V5's
 * `CMAccelerometerData+deviceOrientation.swift`). The orientation can only be unknown if the phone is flat, in which
 * case it is not clear which orientation the phone is held in.
 */
internal val CMAccelerometerData.deviceOrientation: CameraOrientation?
    get() = acceleration.useContents {
        val xNorm = abs(x)
        val yNorm = abs(y)
        val zNorm = abs(z)
        when {
            // If the z-axis is greater than the other axes, the phone is flat.
            zNorm > xNorm && zNorm > yNorm -> null
            xNorm > yNorm -> if (x > 0) CameraOrientation.RIGHT else CameraOrientation.LEFT
            else -> if (y > 0) CameraOrientation.DOWN else CameraOrientation.UP
        }
    }
