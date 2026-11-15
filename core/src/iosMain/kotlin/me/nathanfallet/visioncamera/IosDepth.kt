@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import me.nathanfallet.visioncamera.interop.VCDepthDataRef
import me.nathanfallet.visioncamera.interop.vc_depth_accuracy
import me.nathanfallet.visioncamera.interop.vc_depth_available_types
import me.nathanfallet.visioncamera.interop.vc_depth_calibration
import me.nathanfallet.visioncamera.interop.vc_depth_is_filtered
import me.nathanfallet.visioncamera.interop.vc_depth_map
import me.nathanfallet.visioncamera.interop.vc_depth_object
import me.nathanfallet.visioncamera.interop.vc_depth_quality
import me.nathanfallet.visioncamera.interop.vc_depth_retain
import me.nathanfallet.visioncamera.interop.vc_depth_retain_applying_exif
import me.nathanfallet.visioncamera.interop.vc_depth_retain_converting
import me.nathanfallet.visioncamera.interop.vc_depth_type
import me.nathanfallet.visioncamera.interop.vc_release
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
import kotlin.concurrent.AtomicReference
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
 * A +1 reference to the [AVDepthData] of a [Depth], lazily locked for CPU reads. It is released by [IosDepth.dispose],
 * or as a safety net when its [IosDepth] is garbage collected without having been disposed.
 *
 * The depth data is held as an opaque native reference rather than a Kotlin reference: Kotlin/Native would keep it
 * retained until its garbage collector runs, and the depth buffers of AVFoundation's small pool would run out, stalling
 * the depth stream (see VisionCameraNative.h).
 */
private class DepthDataHolder(depthData: VCDepthDataRef) : LazyLockableBuffer() {
    private val reference = AtomicReference<VCDepthDataRef?>(depthData)

    /** The +1 reference to the depth data, or `null` once released. */
    val depthData: VCDepthDataRef? get() = reference.value

    override val pixelBuffer: CVPixelBufferRef? get() = depthData?.let { vc_depth_map(it) }

    fun release() {
        unlockBuffer()
        reference.getAndSet(null)?.let { vc_release(it) }
    }
}

/**
 * The iOS [Depth], wrapping a +1 reference to an [AVDepthData] (port of V5's `HybridDepth.swift`), which it releases
 * when disposed.
 */
@Suppress("TooManyFunctions")
internal class IosDepth(
    depthData: VCDepthDataRef,
    override val metadata: MediaSampleMetadata,
) : Depth, NativeDepth {
    /** Wraps the given [depthData] (e.g. of a photo), retaining it. */
    constructor(depthData: AVDepthData, metadata: MediaSampleMetadata) :
        this(checkNotNull(vc_depth_retain(depthData)), metadata)

    private val holder = DepthDataHolder(depthData)

    @Suppress("unused", "UnusedPrivateProperty")
    private val cleaner = createCleaner(holder) { it.release() }

    /** The native reference to the depth data, or `null` once disposed. */
    private val reference: VCDepthDataRef? get() = holder.depthData

    // Only for plugins (and conversions): the returned AVDepthData is retained until Kotlin/Native collects it.
    override val depthData: AVDepthData? get() = reference?.let { vc_depth_object(it) }

    private val pixelBuffer: CVPixelBufferRef? get() = holder.pixelBuffer

    override fun dispose() {
        holder.release()
    }

    override val isValid: Boolean get() = reference != null

    override val width: Int get() = pixelBuffer?.let { CVPixelBufferGetWidth(it).toInt() } ?: 0
    override val height: Int get() = pixelBuffer?.let { CVPixelBufferGetHeight(it).toInt() } ?: 0
    override val bytesPerRow: Int get() = pixelBuffer?.let { CVPixelBufferGetBytesPerRow(it).toInt() } ?: 0

    override val timestamp: Long get() = metadata.timestampNanoseconds
    override val orientation: CameraOrientation get() = metadata.orientation
    override val isMirrored: Boolean get() = metadata.isMirrored

    override val pixelFormat: DepthPixelFormat
        get() = reference?.let { depthPixelFormatOf(vc_depth_type(it)) } ?: DepthPixelFormat.UNKNOWN

    override val isDepthDataFiltered: Boolean get() = reference?.let { vc_depth_is_filtered(it) } ?: false

    override val depthDataAccuracy: DepthDataAccuracy
        get() = reference?.let { depthDataAccuracyOf(vc_depth_accuracy(it)) } ?: DepthDataAccuracy.UNKNOWN

    override val depthDataQuality: DepthDataQuality
        get() = reference?.let { depthDataQualityOf(vc_depth_quality(it)) } ?: DepthDataQuality.UNKNOWN

    override val availableDepthPixelFormats: List<DepthPixelFormat>
        get() = reference?.let { vc_depth_available_types(it) }
            ?.mapNotNull { (it as? NSNumber)?.unsignedIntValue ?: (it as? UInt) }
            ?.map { depthPixelFormatOf(it) }
            .orEmpty()

    override val cameraCalibrationData: CameraCalibrationData?
        get() = reference?.let { vc_depth_calibration(it) }?.toCameraCalibrationData()

    override fun rotate(orientation: CameraOrientation, isMirrored: Boolean): Depth {
        val reference = reference ?: throw VisionCameraException("Tried to rotate an already disposed Depth!")
        val cgOrientation = orientation.toCGOrientation(isMirrored)
        return IosDepth(
            depthData = checkNotNull(vc_depth_retain_applying_exif(reference, cgOrientation)),
            metadata = metadata,
        )
    }

    override fun convert(pixelFormat: DepthPixelFormat): Depth {
        val reference = reference ?: throw VisionCameraException("Tried to convert an already disposed Depth!")
        val osFormat = pixelFormat.toCVPixelFormatType()
        val converted = checkNotNull(vc_depth_retain_converting(reference, osFormat))
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
        val depthMap = pixelBuffer
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
        val depthMap = pixelBuffer
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
