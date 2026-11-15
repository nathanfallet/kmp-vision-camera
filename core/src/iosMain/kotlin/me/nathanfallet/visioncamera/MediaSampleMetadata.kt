package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.AVFoundation.AVCapturePhoto
import platform.CoreMedia.CMTime
import platform.Foundation.NSNumber
import platform.ImageIO.kCGImagePropertyOrientation

// Port of V5's Public/MediaSampleMetadata.swift and AVCapturePhoto+getMediaSampleMetadata.swift.

/**
 * The timestamp, orientation and mirroring of a captured media sample.
 */
internal data class MediaSampleMetadata(
    val timestamp: CValue<CMTime>,
    val orientation: CameraOrientation,
    val isMirrored: Boolean,
)

/**
 * Reads the [MediaSampleMetadata] of this photo from its EXIF orientation flag.
 *
 * @throws VisionCameraException If the photo has no valid `kCGImagePropertyOrientation` metadata.
 */
internal fun AVCapturePhoto.getMediaSampleMetadata(): MediaSampleMetadata {
    val exifOrientation = when (val value = metadata[kCGImagePropertyOrientation.toKotlinString()]) {
        is NSNumber -> value.unsignedIntValue
        is Number -> value.toInt().toUInt()
        else -> throw VisionCameraException("Photo did not contain a `kCGImagePropertyOrientation` metadata key!")
    }
    val uiOrientation = uiImageOrientationOfExif(exifOrientation)
    return MediaSampleMetadata(
        timestamp = timestamp,
        orientation = cameraOrientationOf(uiOrientation),
        isMirrored = uiOrientation.isMirrored,
    )
}
