package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.CGImageRepresentation
import platform.AVFoundation.fileDataRepresentation
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.posix.memcpy

/**
 * The iOS [Photo], wrapping an [AVCapturePhoto] (port of V5's `HybridPhoto.swift`).
 */
internal class IosPhoto(
    photo: AVCapturePhoto,
    private val metadata: MediaSampleMetadata,
    override val containerFormat: PhotoContainerFormat,
) : Photo {
    /** The wrapped photo, or `null` once disposed. */
    private var capturedPhoto: AVCapturePhoto? = photo

    private val photo: AVCapturePhoto
        get() = capturedPhoto ?: throw VisionCameraException("This Photo has already been disposed!")

    // Photo metadata

    override val timestamp: Double = CMTimeGetSeconds(photo.timestamp)
    override val isMirrored: Boolean get() = metadata.isMirrored
    override val orientation: CameraOrientation get() = metadata.orientation
    override val isRawPhoto: Boolean = photo.rawPhoto

    private val size: Size = if (isRawPhoto) {
        photo.resolvedSettings.rawPhotoDimensions.toSize()
    } else {
        photo.resolvedSettings.photoDimensions.toSize()
    }
    override val width: Int get() = size.width
    override val height: Int get() = size.height

    // Pixel Buffer access

    override val hasPixelBuffer: Boolean get() = capturedPhoto?.pixelBuffer != null

    override fun getPixelBuffer(): ByteArray {
        val pixelBuffer = photo.pixelBuffer ?: throw VisionCameraException(
            "This Photo does not contain a pixel buffer! " +
                "Check `photo.hasPixelBuffer` before calling `photo.getPixelBuffer()`",
        )
        CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        try {
            val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
                ?: throw VisionCameraException("Failed to get CVPixelBuffer's base address!")
            val size = CVPixelBufferGetDataSize(pixelBuffer)
            val bytes = ByteArray(size.toInt())
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), baseAddress, size) }
            }
            return bytes
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    // File Data

    private fun fileData(): NSData =
        photo.fileDataRepresentation() ?: throw VisionCameraException("Failed to get photo's file data!")

    override suspend fun getFileData(): ByteArray = withContext(Dispatchers.Default) {
        fileData().toByteArray()
    }

    // Saving Photo to File

    private fun saveImage(path: String) {
        if (!fileData().writeToFile(path, atomically = false)) {
            throw VisionCameraException("Failed to write the Photo to \"$path\"!")
        }
    }

    override suspend fun saveToFile(path: String) = withContext(Dispatchers.Default) {
        saveImage(path)
    }

    override suspend fun saveToTemporaryFile(): String = withContext(Dispatchers.Default) {
        // 1. Create temp path
        val path = createTempFilePath(containerFormat.toUTType())
        // 2. Save image
        saveImage(path)
        // 3. Return path
        path
    }

    // Conversion

    /**
     * Converts this photo to a [UIImage], with its [orientation] and mirroring applied via `UIImage.imageOrientation`.
     *
     * @throws VisionCameraException If this photo cannot be converted (e.g. it is a RAW photo).
     */
    @OptIn(BetaInteropApi::class)
    fun toUIImage(): UIImage = autoreleasepool {
        val cgImage = photo.CGImageRepresentation() ?: throw VisionCameraException(
            if (isRawPhoto) {
                "Failed to get Image data! Raw Photos cannot be converted to Images."
            } else {
                "Failed to get Image data!"
            },
        )
        UIImage.imageWithCGImage(cgImage, scale = 1.0, orientation = orientation.toUIImageOrientation(isMirrored))
    }

    override fun dispose() {
        capturedPhoto = null
    }

    override fun toString(): String = "Photo(${width}x$height, $containerFormat, $orientation)"
}
