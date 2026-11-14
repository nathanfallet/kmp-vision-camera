package me.nathanfallet.visioncamera

import kotlinx.coroutines.CompletableDeferred
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDevicePositionUnspecified
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureDeviceTypeExternal
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.constituentDevices
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_t

// Port of V5's AVCaptureDevice+defaultFor.swift, AVCaptureDevice+resolve.swift, AVCaptureDevice+withLock.swift,
// AVCaptureDevice+minTorchStrength.swift and AVCaptureDevice+maxTorchStrength.swift.

/** All [AVCaptureDeviceFormat]s of this device. */
internal val AVCaptureDevice.deviceFormats: List<AVCaptureDeviceFormat>
    get() = formats.filterIsInstance<AVCaptureDeviceFormat>()

/** All constituent devices of this virtual device, or an empty list for physical devices. */
internal val AVCaptureDevice.constituentCaptureDevices: List<AVCaptureDevice>
    get() = constituentDevices.filterIsInstance<AVCaptureDevice>()

/**
 * The minimum supported torch strength level for this device.
 *
 * `setTorchModeOnWithLevel` accepts `(0, AVCaptureMaxAvailableTorchLevel]` and rejects exactly `0` with an
 * uncatchable `NSInvalidArgumentException`, so the lowest usable brightness is just above `0`.
 */
@Suppress("UnusedReceiverParameter", "MagicNumber")
internal val AVCaptureDevice.minTorchStrength: Double
    get() = 0.001

/**
 * The maximum supported torch strength level for this device. `setTorchModeOnWithLevel` accepts values up to
 * `AVCaptureMaxAvailableTorchLevel`, which is always `1.0`.
 */
@Suppress("UnusedReceiverParameter")
internal val AVCaptureDevice.maxTorchStrength: Double
    get() = 1.0

/**
 * Gets the platform's default [AVCaptureDevice] at the given [position], if any.
 */
internal fun defaultCaptureDevice(position: TargetCameraPosition): AVCaptureDevice? = when (position) {
    // Get default wide-angle at .back
    TargetCameraPosition.BACK -> AVCaptureDevice.defaultDeviceWithDeviceType(
        AVCaptureDeviceTypeBuiltInWideAngleCamera,
        AVMediaTypeVideo,
        AVCaptureDevicePositionBack,
    )
    // Get default wide-angle at .front
    TargetCameraPosition.FRONT -> AVCaptureDevice.defaultDeviceWithDeviceType(
        AVCaptureDeviceTypeBuiltInWideAngleCamera,
        AVMediaTypeVideo,
        AVCaptureDevicePositionFront,
    )
    // On iOS, Position "external" is for some reason reflected on the .deviceType, not on .position.
    TargetCameraPosition.EXTERNAL -> if (isIOSAtLeast(major = 17)) {
        AVCaptureDevice.defaultDeviceWithDeviceType(
            AVCaptureDeviceTypeExternal,
            AVMediaTypeVideo,
            AVCaptureDevicePositionUnspecified,
        )
    } else {
        null
    }
}

/**
 * Resolves the given [CameraDeviceOrPosition] to an [AVCaptureDevice].
 *
 * @throws VisionCameraException If the given device is not an iOS [CameraDevice], or if there is no device at the
 * given position.
 */
internal fun resolveCaptureDevice(value: CameraDeviceOrPosition): AVCaptureDevice = when (value) {
    is IosCameraDevice -> value.device
    is CameraDevice -> throw VisionCameraException("CameraDevice \"$value\" is not an iOS CameraDevice!")
    is TargetCameraPosition -> defaultCaptureDevice(value)
        ?: throw VisionCameraException("No CameraDevice exists at position \"$value\"!")
}

/**
 * Locks this device for configuration.
 *
 * @throws VisionCameraException If the device could not be locked.
 */
internal fun AVCaptureDevice.lockForConfigurationOrThrow() {
    throwingNSError("Failed to lock CameraDevice \"$uniqueID\" for configuration") { error ->
        lockForConfiguration(error)
    }
}

/**
 * Runs the given [block] under an [AVCaptureDevice] lock, on the given [queue], and suspends until [block] called
 * `resolve` or `reject`. Once [block] returns, the [AVCaptureDevice] is unlocked again.
 */
internal suspend fun AVCaptureDevice.withLock(
    queue: dispatch_queue_t,
    block: (resolve: () -> Unit, reject: (Throwable) -> Unit) -> Unit,
) {
    val result = CompletableDeferred<Unit>()
    dispatch_async(queue) {
        try {
            // 1. Lock the device
            lockForConfigurationOrThrow()
            try {
                // 2. Call the block and pass the completion callbacks
                block({ result.complete(Unit) }, { result.completeExceptionally(it) })
            } finally {
                // 3. Once the block returned, we can unlock the device again
                unlockForConfiguration()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            result.completeExceptionally(e)
        }
    }
    result.await()
}

/**
 * Runs the given [block] under an [AVCaptureDevice] lock, on the given [queue], and suspends until it returned.
 */
internal suspend fun AVCaptureDevice.withLock(queue: dispatch_queue_t, block: () -> Unit) {
    withLock(queue) { resolve, _ ->
        block()
        resolve()
    }
}
