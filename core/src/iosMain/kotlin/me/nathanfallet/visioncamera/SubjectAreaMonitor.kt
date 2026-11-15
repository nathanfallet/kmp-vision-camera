package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceSubjectAreaDidChangeNotification
import platform.AVFoundation.subjectAreaChangeMonitoringEnabled
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObjectProtocol

/**
 * Coordinates `AVCaptureDevice.subjectAreaChangeMonitoringEnabled` across multiple observers and multiple
 * [CameraController] instances per device (port of V5's `SubjectAreaMonitor.swift`).
 *
 * The underlying AVFoundation flag is a single-writer state on the device: enabling it for one observer and
 * disabling it for another would clobber whichever ran last. This type ref-counts observers per device so that
 * monitoring is only enabled while at least one observer is registered, and only disabled when the final observer
 * is removed.
 */
internal object SubjectAreaMonitor {
    private class Entry(val device: AVCaptureDevice) {
        val tokens = mutableMapOf<Long, NSObjectProtocol>()
    }

    private val entries = mutableMapOf<String, Entry>()
    private val lock = NSLock()
    private var nextListenerID = 0L

    /**
     * Registers an observer for `AVCaptureDeviceSubjectAreaDidChangeNotification` on the given [device].
     *
     * Enables `subjectAreaChangeMonitoringEnabled` on the [device] if this is the first observer. Returns a
     * [ListenerSubscription], which removes this observer and disables monitoring if it was the last one across all
     * callers.
     */
    fun addObserver(device: AVCaptureDevice, onSubjectAreaDidChange: () -> Unit): ListenerSubscription {
        val deviceKey = device.uniqueID
        val listenerID = withLock {
            val listenerID = nextListenerID++
            val entry = entries[deviceKey] ?: Entry(device)
            if (entry.tokens.isEmpty()) {
                setMonitoringEnabled(device, enabled = true)
            }
            val token = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVCaptureDeviceSubjectAreaDidChangeNotification,
                `object` = device,
                queue = null,
            ) { _ -> onSubjectAreaDidChange() }
            entry.tokens[listenerID] = token
            entries[deviceKey] = entry
            listenerID
        }
        return ListenerSubscription { removeObserver(deviceKey, listenerID) }
    }

    private fun removeObserver(deviceKey: String, listenerID: Long) = withLock {
        val entry = entries[deviceKey] ?: return@withLock
        val token = entry.tokens.remove(listenerID) ?: return@withLock
        NSNotificationCenter.defaultCenter.removeObserver(token)
        if (entry.tokens.isEmpty()) {
            setMonitoringEnabled(entry.device, enabled = false)
            entries.remove(deviceKey)
        }
    }

    private fun setMonitoringEnabled(device: AVCaptureDevice, enabled: Boolean) {
        try {
            device.lockForConfigurationOrThrow()
            device.subjectAreaChangeMonitoringEnabled = enabled
            device.unlockForConfiguration()
            Logger.info("${if (enabled) "Enabled" else "Disabled"} subject area change monitoring.")
        } catch (e: VisionCameraException) {
            Logger.error("Failed to set subject area monitoring to $enabled: $e")
        }
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
