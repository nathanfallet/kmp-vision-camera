@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFoundation.AVDepthData
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMSampleBufferCreateReadyWithImageBuffer
import platform.CoreMedia.CMSampleBufferRefVar
import platform.CoreMedia.CMSampleTimingInfo
import platform.CoreMedia.CMVideoFormatDescriptionCreateForImageBuffer
import platform.CoreMedia.CMVideoFormatDescriptionRefVar
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.Foundation.NSNumber
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

/**
 * Gives access to the native [AVDepthData] of a [Depth] (port of V5's `Public/NativeDepth.swift`).
 *
 * This is an extension point for plugin modules, which read the depth's native data through it.
 */
interface NativeDepth {
    /** The depth data, or `null` once it has been disposed. */
    val depthData: AVDepthData?

    /** The depth's timestamp, orientation and mirroring. */
    val metadata: MediaSampleMetadata
}

/**
 * The [AVDepthData] of a [Depth], lazily locked for CPU reads. It is released by [IosDepth.dispose], or as a safety net
 * when its [IosDepth] is garbage collected without having been disposed.
 */
private class DepthDataHolder(depthData: AVDepthData) : LazyLockableBuffer() {
    var depthData: AVDepthData? = depthData
        private set

    override val pixelBuffer: CVPixelBufferRef? get() = depthData?.depthDataMap

    fun release() {
        unlockBuffer()
        depthData = null
    }
}

/**
 * The iOS [Depth], wrapping an [AVDepthData] (port of V5's `HybridDepth.swift`).
 */
@Suppress("TooManyFunctions")
internal class IosDepth(
    depthData: AVDepthData,
    override val metadata: MediaSampleMetadata,
) : Depth, NativeDepth {
    private val holder = DepthDataHolder(depthData)

    @Suppress("unused", "UnusedPrivateProperty")
    private val cleaner = createCleaner(holder) { it.release() }

    override val depthData: AVDepthData? get() = holder.depthData

    private val pixelBuffer: CVPixelBufferRef? get() = holder.pixelBuffer

    override fun dispose() {
        holder.release()
    }

    override val isValid: Boolean get() = depthData != null

    override val width: Int get() = pixelBuffer?.let { CVPixelBufferGetWidth(it).toInt() } ?: 0
    override val height: Int get() = pixelBuffer?.let { CVPixelBufferGetHeight(it).toInt() } ?: 0
    override val bytesPerRow: Int get() = pixelBuffer?.let { CVPixelBufferGetBytesPerRow(it).toInt() } ?: 0

    override val timestamp: Long get() = metadata.timestampNanoseconds
    override val orientation: CameraOrientation get() = metadata.orientation
    override val isMirrored: Boolean get() = metadata.isMirrored

    override val pixelFormat: DepthPixelFormat
        get() = depthData?.let { depthPixelFormatOf(it.depthDataType) } ?: DepthPixelFormat.UNKNOWN

    override val isDepthDataFiltered: Boolean get() = depthData?.depthDataFiltered ?: false

    override val depthDataAccuracy: DepthDataAccuracy
        get() = depthData?.let { depthDataAccuracyOf(it.depthDataAccuracy) } ?: DepthDataAccuracy.UNKNOWN

    override val depthDataQuality: DepthDataQuality
        get() = depthData?.let { depthDataQualityOf(it.depthDataQuality) } ?: DepthDataQuality.UNKNOWN

    override val availableDepthPixelFormats: List<DepthPixelFormat>
        get() = depthData?.availableDepthDataTypes
            ?.mapNotNull { (it as? NSNumber)?.unsignedIntValue ?: (it as? UInt) }
            ?.map { depthPixelFormatOf(it) }
            .orEmpty()

    override val cameraCalibrationData: CameraCalibrationData?
        get() = depthData?.cameraCalibrationData?.toCameraCalibrationData()

    override fun rotate(orientation: CameraOrientation, isMirrored: Boolean): Depth {
        val depthData = depthData ?: throw VisionCameraException("Tried to rotate an already disposed Depth!")
        val cgOrientation = orientation.toCGOrientation(isMirrored)
        val rotated = depthData.depthDataByApplyingExifOrientation(cgOrientation)
        return IosDepth(depthData = rotated, metadata = metadata)
    }

    override fun convert(pixelFormat: DepthPixelFormat): Depth {
        val depthData = depthData ?: throw VisionCameraException("Tried to convert an already disposed Depth!")
        val osFormat = pixelFormat.toCVPixelFormatType()
        val converted = depthData.depthDataByConvertingToDepthDataType(osFormat)
        return IosDepth(depthData = converted, metadata = metadata)
    }

    override fun convertCameraPointToDepthPoint(cameraPoint: Point): Point {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Depth has already been disposed!")
        val matrix = FrameCoordinateSystemConverter.getCameraToFrameMatrix(pixelBuffer, orientation, isMirrored)
        return cameraPoint.applying(matrix)
    }

    override fun convertDepthPointToCameraPoint(depthPoint: Point): Point {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Depth has already been disposed!")
        val matrix = FrameCoordinateSystemConverter.getFrameToCameraMatrix(pixelBuffer, orientation, isMirrored)
        return depthPoint.applying(matrix)
    }

    override fun getDepthData(): ByteArray {
        val depthMap = depthData?.depthDataMap
            ?: throw VisionCameraException("Cannot get an already disposed Depth Frame's data!")
        holder.ensureBufferLocked()
        return depthMap.copyToByteArray()
    }

    override fun getNativeBuffer(): NativeBuffer {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Depth has already been disposed!")
        return pixelBuffer.asNativeBuffer()
    }

    @Suppress("ThrowsCount")
    override fun toFrame(): Frame {
        val depthMap = depthData?.depthDataMap
            ?: throw VisionCameraException("Cannot convert an already disposed Depth to a Frame!")
        return memScoped {
            val format = alloc<CMVideoFormatDescriptionRefVar>()
            val formatStatus = CMVideoFormatDescriptionCreateForImageBuffer(null, depthMap, format.ptr)
            val formatDescription = format.value
            if (formatStatus != NO_ERROR || formatDescription == null) {
                throw VisionCameraException("Failed to create the Depth's format description! (status: $formatStatus)")
            }
            try {
                val timing = alloc<CMSampleTimingInfo>()
                zeroCMTime.place(timing.duration.ptr)
                metadata.timestamp.place(timing.presentationTimeStamp.ptr)
                zeroCMTime.place(timing.decodeTimeStamp.ptr)
                val sampleBuffer = alloc<CMSampleBufferRefVar>()
                val status = CMSampleBufferCreateReadyWithImageBuffer(
                    null,
                    depthMap,
                    formatDescription,
                    timing.ptr,
                    sampleBuffer.ptr,
                )
                val buffer = sampleBuffer.value
                if (status != NO_ERROR || buffer == null) {
                    throw VisionCameraException("Failed to convert the Depth to a Frame! (status: $status)")
                }
                // The Frame retains the buffer itself, so we release our +1 from `Create`.
                IosFrame(buffer = buffer, metadata = metadata).also { CFRelease(buffer) }
            } finally {
                CFRelease(formatDescription)
            }
        }
    }

    override fun toString(): String = "Depth(${width}x$height, $pixelFormat, $orientation)"

    private companion object {
        /** `noErr`. */
        const val NO_ERROR = 0
    }
}
