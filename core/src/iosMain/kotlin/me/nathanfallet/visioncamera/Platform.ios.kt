package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_attr_make_with_qos_class
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

/**
 * Whether the current iOS version is at least [major].[minor], the equivalent of Swift's `#available(iOS x.y, *)`.
 *
 * APIs introduced after the deployment target must be guarded with this check, as they are not available at runtime
 * on older iOS versions.
 */
internal fun isIOSAtLeast(major: Long, minor: Long = 0): Boolean {
    val version = cValue<NSOperatingSystemVersion> {
        majorVersion = major
        minorVersion = minor
        patchVersion = 0
    }
    return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(version)
}

/**
 * A minimal logger writing to the unified system log, like V5's `logger`.
 */
internal object Logger {
    fun info(message: String) = NSLog("VisionCamera: %@", message)

    fun error(message: String) = NSLog("VisionCamera [ERROR]: %@", message)
}

/**
 * Creates a serial dispatch queue with the given [label] and user-interactive QoS, like V5's session queue.
 */
internal fun createSerialQueue(label: String): dispatch_queue_t =
    dispatch_queue_create(label, dispatch_queue_attr_make_with_qos_class(null, QOS_CLASS_USER_INTERACTIVE, 0))

/** `QOS_CLASS_USER_INTERACTIVE` from `<sys/qos.h>`. */
private const val QOS_CLASS_USER_INTERACTIVE: UInt = 0x21u

/**
 * Runs the given [block] on this serial dispatch queue, and suspends until it returned (the equivalent of V5's
 * `Promise.parallel(queue) { ... }`).
 */
internal suspend fun <T> dispatch_queue_t.await(block: () -> T): T {
    val result = CompletableDeferred<T>()
    dispatch_async(this) {
        try {
            result.complete(block())
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            result.completeExceptionally(e)
        }
    }
    return result.await()
}

/**
 * Runs the given [block] on the main thread: synchronously if we already are on the main thread, asynchronously
 * otherwise.
 */
internal fun runOnMainThread(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}

/**
 * Calls the given Objective-C [call] taking an `NSError**` out-parameter, and throws a [VisionCameraException] with
 * the given [message] if it failed (returned `false` or `null`).
 */
@OptIn(BetaInteropApi::class)
internal inline fun <T> throwingNSError(
    message: String,
    call: (error: CPointer<ObjCObjectVar<NSError?>>) -> T,
): T & Any = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val result = call(error.ptr)
    if (result == null || result == false) {
        throw VisionCameraException("$message: ${error.value?.localizedDescription ?: "Unknown error"}")
    }
    result
}

/**
 * Wraps an [NSError] into a [VisionCameraException].
 */
internal fun NSError.toException(): VisionCameraException =
    VisionCameraException("$localizedDescription (domain: $domain, code: $code)")
