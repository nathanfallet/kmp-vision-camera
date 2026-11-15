package me.nathanfallet.visioncamera

import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCaptureResolvedPhotoSettings
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * The [AVCapturePhotoCaptureDelegateProtocol] of a single [CameraPhotoOutput.capturePhoto] call (port of V5's
 * `CapturePhotoDelegate.swift`).
 */
internal class CapturePhotoDelegate(
    private val onCaptured: (AVCapturePhoto, MediaSampleMetadata) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val callbacks: CapturePhotoCallbacks,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    private var didResolve = false

    init {
        // Add a static strong reference while capture is in progress, because the `AVCapturePhotoOutput` only
        // references its delegate weakly.
        ActiveDelegates.retain(this)
    }

    // 1. Begin capture process
    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        willBeginCaptureForResolvedSettings: AVCaptureResolvedPhotoSettings,
    ) {
        callbacks.onWillBeginCapture?.invoke()
    }

    // 2. About to capture photo
    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        willCapturePhotoForResolvedSettings: AVCaptureResolvedPhotoSettings,
    ) {
        callbacks.onWillCapturePhoto?.invoke()
    }

    // 3. Captured actual photo (or error)
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        if (error != null) {
            // An error occurred - reject with it
            onError(error.toException())
        } else {
            try {
                val metadata = didFinishProcessingPhoto.getMediaSampleMetadata()
                // Resolve callback with photo
                onCaptured(didFinishProcessingPhoto, metadata)
            } catch (e: VisionCameraException) {
                // Resolve callback with error
                onError(e)
            }
        }
        didResolve = true
    }

    // 4. Photo capture done
    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didCapturePhotoForResolvedSettings: AVCaptureResolvedPhotoSettings,
    ) {
        callbacks.onDidCapturePhoto?.invoke()
    }

    // 5. Capture process done
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishCaptureForResolvedSettings: AVCaptureResolvedPhotoSettings,
        error: NSError?,
    ) {
        if (error != null && !didResolve) onError(error.toException())
        // Remove the static strong reference, we're done
        ActiveDelegates.release(this)
    }
}
