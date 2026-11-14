package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.activeColorSpace
import platform.AVFoundation.activeDepthDataFormat
import platform.AVFoundation.automaticallyAdjustsVideoHDREnabled
import platform.AVFoundation.videoHDREnabled
import platform.CoreMedia.CMTimeMake

// Part of V5's `HybridCameraSession.configureConnection(...)`: applies resolved constraints to a device.

/**
 * Applies the [resolved] constraints (format, depth format, FPS, stabilization and dynamic range) to the given
 * [device] of this session.
 *
 * The [device] must already be locked for configuration.
 */
internal fun AVCaptureSession.applyResolvedConstraints(device: AVCaptureDevice, resolved: ResolvedConstraints) {
    val enabledConstraints = resolved.enabledConstraints
    device.applyNegotiatedFormat(resolved.negotiatedFormat)
    enabledConstraints.selectedFPS?.let { device.applyFPS(it) }
    enabledConstraints.selectedVideoStabilizationMode?.let { applyStabilizationMode(it, toPreview = false) }
    enabledConstraints.selectedPreviewStabilizationMode?.let { applyStabilizationMode(it, toPreview = true) }
    enabledConstraints.selectedVideoDynamicRange?.let { device.applyDynamicRange(it) }
}

private fun AVCaptureDevice.applyNegotiatedFormat(negotiatedFormat: NegotiatedFormat) {
    if (activeFormat != negotiatedFormat.format) {
        activeFormat = negotiatedFormat.format
    }
    // On devices that support depth, `device.activeDepthDataFormat` cannot be set to `nil`, so we only set the
    // depth format when we actually have a `negotiatedFormat.depthFormat`. Otherwise, it's implicitly `nil`.
    val depthFormat = negotiatedFormat.depthFormat
    if (depthFormat != null && activeDepthDataFormat != depthFormat) {
        activeDepthDataFormat = depthFormat
    }
}

private fun AVCaptureDevice.applyFPS(fps: Double) {
    activeVideoMaxFrameDuration = CMTimeMake(value = 1, timescale = fps.toInt())
    activeVideoMinFrameDuration = CMTimeMake(value = 1, timescale = fps.toInt())
}

/**
 * Video and Preview stabilization are configured on the session's connections: the preview connections if
 * [toPreview] is `true`, all other connections otherwise.
 */
private fun AVCaptureSession.applyStabilizationMode(mode: TargetStabilizationMode, toPreview: Boolean) {
    sessionConnections
        .filter { it.isConnectedToPreview == toPreview && it.supportsVideoStabilization }
        .forEach { it.preferredVideoStabilizationMode = mode.toAVCaptureVideoStabilizationMode() }
}

private fun AVCaptureDevice.applyDynamicRange(dynamicRange: TargetDynamicRange) {
    // Bit-Depth + ColorRange is implicitly set via `activeFormat`'s pixel format
    activeColorSpace = dynamicRange.colorSpace.toAVCaptureColorSpace()
    if (!activeFormat.videoHDRSupported) return
    if (dynamicRange.isHDR) {
        // Effectively allow auto-switching to EDR
        automaticallyAdjustsVideoHDREnabled = true
    } else {
        // Effectively disable EDR entirely.
        automaticallyAdjustsVideoHDREnabled = false
        videoHDREnabled = false
    }
}
