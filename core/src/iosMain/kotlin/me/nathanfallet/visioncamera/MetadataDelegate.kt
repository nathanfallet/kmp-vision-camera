package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVMetadataObject
import platform.darwin.NSObject
import kotlin.concurrent.AtomicReference

/**
 * The [AVCaptureMetadataOutputObjectsDelegateProtocol] of a metadata output (port of V5's `MetadataDelegate.swift`).
 *
 * The callback can be set from any thread; it is called on the output's queue.
 */
internal class MetadataDelegate : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private val onMetadataObjectsRef = AtomicReference<((List<AVMetadataObject>) -> Unit)?>(null)

    /** Called with the metadata objects detected in each frame. */
    var onMetadataObjects: ((List<AVMetadataObject>) -> Unit)?
        get() = onMetadataObjectsRef.value
        set(value) {
            onMetadataObjectsRef.value = value
        }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        onMetadataObjects?.invoke(didOutputMetadataObjects.filterIsInstance<AVMetadataObject>())
    }
}
