package me.nathanfallet.visioncamera

import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVReturnSuccess

// Port of V5's Utils/LazyLockablePixelBuffer.swift.

/**
 * A buffer whose [pixelBuffer] is lazily locked for CPU reads the first time it is accessed, and unlocked when it is
 * released (V5's `LazyLockableBuffer`).
 */
internal abstract class LazyLockableBuffer {
    private var isLocked = false

    /** The pixel buffer to lock, or `null` once released. */
    abstract val pixelBuffer: CVPixelBufferRef?

    /**
     * Locks the [pixelBuffer] for CPU reads, if it is not locked yet.
     *
     * @throws VisionCameraException If the buffer has already been released, or cannot be locked.
     */
    fun ensureBufferLocked() {
        if (isLocked) return
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("Failed to lock already disposed PixelBuffer!")
        val result = CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        if (result != kCVReturnSuccess) throw VisionCameraException("Failed to lock PixelBuffer $pixelBuffer!")
        isLocked = true
    }

    /** Unlocks the [pixelBuffer], if it has been locked. */
    fun unlockBuffer() {
        if (!isLocked) return
        val pixelBuffer = pixelBuffer ?: return
        val result = CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        if (result != kCVReturnSuccess) Logger.error("Failed to unlock PixelBuffer!")
        isLocked = false
    }
}
