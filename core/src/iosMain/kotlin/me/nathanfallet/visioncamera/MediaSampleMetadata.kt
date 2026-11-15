package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCapturePhoto
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeConvertScale
import platform.CoreMedia.kCMTimeRoundingMethod_Default
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
) {
    /** The [timestamp], in nanoseconds. */
    val timestampNanoseconds: Long
        get() = CMTimeConvertScale(timestamp, NANOSECONDS_PER_SECOND, kCMTimeRoundingMethod_Default)
            .useContents { value }

    companion object {
        /** The timescale of nanosecond timestamps. */
        private const val NANOSECONDS_PER_SECOND = 1_000_000_000

        /** Reads the orientation and mirroring of a sample delivered at [timestamp] from the given [connection]. */
        fun fromConnection(timestamp: CValue<CMTime>, connection: AVCaptureConnection): MediaSampleMetadata =
            MediaSampleMetadata(
                timestamp = timestamp,
                orientation = connection.orientation,
                isMirrored = connection.videoMirrored,
            )

        /**
         * Computes the [MediaSampleMetadata] of a buffer delivered by a frame or depth output (V5's
         * `getMediaSampleMetadata(at:orientation:isMirrored:)` of `HybridCameraFrameOutput.swift` and
         * `HybridCameraDepthFrameOutput.swift`): relative to the output's [mirrorMode] and [outputOrientation].
         */
        fun relativeToOutput(
            timestamp: CValue<CMTime>,
            bufferOrientation: CameraOrientation,
            isBufferMirrored: Boolean,
            mirrorMode: MirrorMode,
            outputOrientation: CameraOrientation,
        ): MediaSampleMetadata {
            // `isMirrored` is relative; if the buffer is already mirrored & we want mirror, good.
            // If not, we need to counter-mirror.
            val isMirrored = when (mirrorMode) {
                // We just follow along with what the connection gives us.
                MirrorMode.AUTO -> false
                // We enabled mirroring. If the buffer is not mirrored, we need to mirror.
                MirrorMode.ON -> !isBufferMirrored
                // We disabled mirroring. If the buffer is mirrored, we need to counter-mirror.
                MirrorMode.OFF -> isBufferMirrored
            }
            // `orientation` is relative to the target output orientation.
            val relativeOrientation = bufferOrientation.relativeTo(outputOrientation)
            return MediaSampleMetadata(
                timestamp = timestamp,
                orientation = relativeOrientation,
                isMirrored = isMirrored,
            )
        }
    }
}

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
