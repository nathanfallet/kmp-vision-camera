package me.nathanfallet.visioncamera.barcode

import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.CoreMedia.CMSampleBufferRef
import platform.darwin.NSObject

/**
 * The [AVCaptureVideoDataOutputSampleBufferDelegateProtocol] of the Barcode Scanner output (port of V5's
 * `BarcodeScannerDelegate.swift`). The buffer is only valid during [onSampleBuffer]: retain it to use it later.
 */
internal class BarcodeScannerDelegate(
    private val onSampleBuffer: (CMSampleBufferRef) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val buffer = didOutputSampleBuffer ?: return
        onSampleBuffer(buffer)
    }
}
