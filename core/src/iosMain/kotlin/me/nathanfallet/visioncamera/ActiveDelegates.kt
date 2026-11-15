package me.nathanfallet.visioncamera

import platform.Foundation.NSLock

/**
 * Strong references to the delegates of in-flight captures and recordings (V5's static `delegates` arrays in
 * `CapturePhotoDelegate.swift` and `VideoDelegate.swift`): `AVCapturePhotoOutput` and `AVCaptureFileOutput` only
 * reference their delegates weakly, so they would otherwise be garbage collected mid-capture.
 */
internal object ActiveDelegates {
    private val delegates = mutableSetOf<Any>()
    private val lock = NSLock()

    /** Keeps the given [delegate] alive until [release] is called. */
    fun retain(delegate: Any) {
        lock.lock()
        try {
            delegates.add(delegate)
        } finally {
            lock.unlock()
        }
    }

    /** Releases the given [delegate], retained with [retain]. */
    fun release(delegate: Any) {
        lock.lock()
        try {
            delegates.remove(delegate)
        } finally {
            lock.unlock()
        }
    }
}
