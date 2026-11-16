package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetInputPriority
import platform.AVFoundation.AVCaptureSessionPresetPhoto

/**
 * Picks the session preset for RAW photos, after the negotiated formats are applied.
 *
 * AVFoundation only offers Bayer RAW photo formats with the photo session preset, never with
 * [AVCaptureSessionPresetInputPriority], which applying the negotiated formats implies. V5 always stays on it, so its
 * RAW captures fail on e.g. an iPhone 11. So a session with a RAW PhotoOutput ([capturesRawPhotos]) uses the photo
 * preset instead, which picks the device's photo format itself. Any other session goes back to the input priority
 * preset.
 *
 * Multi-cam sessions only support the input priority preset, so they stay on it.
 *
 * @return Whether the photo preset is used.
 */
internal fun AVCaptureSession.applyRawPhotoPreset(capturesRawPhotos: Boolean): Boolean {
    val usesPhotoPreset = capturesRawPhotos && canSetSessionPreset(AVCaptureSessionPresetPhoto)
    val preset = if (usesPhotoPreset) AVCaptureSessionPresetPhoto else AVCaptureSessionPresetInputPriority
    if (sessionPreset != preset) {
        Logger.info("Switching the CameraSession to the $preset preset (RAW photos: $capturesRawPhotos)")
        sessionPreset = preset
    }
    return usesPhotoPreset
}
