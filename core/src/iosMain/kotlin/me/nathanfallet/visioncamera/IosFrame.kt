@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferInvalidate
import platform.CoreMedia.CMSampleBufferIsValid
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetPlaneCount
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferIsPlanar
import platform.CoreVideo.CVPixelBufferRef
import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

/**
 * Gives access to the native `CMSampleBuffer` of a [Frame] (port of V5's `Public/NativeFrame.swift`).
 *
 * This is an extension point for plugin modules, which read the frame's native buffer through it:
 * ```kotlin
 * val sampleBuffer = (frame as NativeFrame).sampleBuffer
 * val pixelBuffer = sampleBuffer?.let { CMSampleBufferGetImageBuffer(it) }
 * ```
 */
interface NativeFrame {
    /** The frame's sample buffer, or `null` once it has been disposed. It is invalidated when the frame is disposed. */
    val sampleBuffer: CMSampleBufferRef?

    /** The frame's timestamp, orientation and mirroring. */
    val metadata: MediaSampleMetadata
}

/**
 * A +1 reference to a frame's `CMSampleBuffer`, lazily locked for CPU reads. It is released by [IosFrame.dispose], or
 * as a safety net when its [IosFrame] is garbage collected without having been disposed (Swift's ARC does that in V5).
 */
private class RetainedSampleBuffer(sampleBuffer: CMSampleBufferRef) : LazyLockableBuffer() {
    private val buffer = AtomicReference<CMSampleBufferRef?>(sampleBuffer.also { CFRetain(it) })

    val sampleBuffer: CMSampleBufferRef? get() = buffer.value

    override val pixelBuffer: CVPixelBufferRef?
        get() = sampleBuffer?.let { CMSampleBufferGetImageBuffer(it) }

    fun release() {
        unlockBuffer()
        val sampleBuffer = buffer.getAndSet(null) ?: return
        CMSampleBufferInvalidate(sampleBuffer)
        CFRelease(sampleBuffer)
    }
}

/**
 * The iOS [Frame], wrapping a `CMSampleBuffer` (port of V5's `HybridFrame.swift`).
 */
internal class IosFrame(
    buffer: CMSampleBufferRef,
    override val metadata: MediaSampleMetadata,
) : Frame, NativeFrame {
    private val retainedBuffer = RetainedSampleBuffer(buffer)

    @Suppress("unused", "UnusedPrivateProperty")
    private val cleaner = createCleaner(retainedBuffer) { it.release() }

    private var planesCached: List<IosFramePlane>? = null

    override val sampleBuffer: CMSampleBufferRef? get() = retainedBuffer.sampleBuffer

    /** The frame's pixel buffer, or `null` once it has been disposed. */
    val pixelBuffer: CVPixelBufferRef? get() = retainedBuffer.pixelBuffer

    override val timestamp: Long get() = metadata.timestampNanoseconds
    override val isMirrored: Boolean get() = metadata.isMirrored
    override val orientation: CameraOrientation get() = metadata.orientation

    override val width: Int get() = pixelBuffer?.let { CVPixelBufferGetWidth(it).toInt() } ?: 0
    override val height: Int get() = pixelBuffer?.let { CVPixelBufferGetHeight(it).toInt() } ?: 0
    override val bytesPerRow: Int get() = pixelBuffer?.let { CVPixelBufferGetBytesPerRow(it).toInt() } ?: 0

    override val isValid: Boolean get() = sampleBuffer?.let { CMSampleBufferIsValid(it) } ?: false
    override val isPlanar: Boolean get() = pixelBuffer?.let { CVPixelBufferIsPlanar(it) } ?: false
    override val hasPixelBuffer: Boolean get() = pixelBuffer != null
    override val hasNativeBuffer: Boolean get() = pixelBuffer != null

    override val pixelFormat: PixelFormat
        get() = pixelBuffer?.let { pixelFormatOf(CVPixelBufferGetPixelFormatType(it)) } ?: VideoPixelFormat.UNKNOWN

    override val cameraIntrinsicMatrix: List<Double>? by lazy { sampleBuffer?.getCameraIntrinsicMatrix() }

    override fun dispose() {
        retainedBuffer.release()
        planesCached?.forEach { it.dispose() }
    }

    override fun getPlanes(): List<FramePlane> {
        if (!isValid) throw VisionCameraException("This Frame has already been disposed!")
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Frame does not contain a Pixel Buffer!")
        // we have planes cached
        planesCached?.let { return it }
        retainedBuffer.ensureBufferLocked()
        val planeCount = CVPixelBufferGetPlaneCount(pixelBuffer).toInt()
        val planes = (0 until planeCount).map { index -> IosFramePlane(frame = this, planeIndex = index) }
        planesCached = planes
        return planes
    }

    override fun getPixelBuffer(): ByteArray {
        if (!isValid) throw VisionCameraException("This Frame has already been disposed!")
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Frame does not contain a Pixel Buffer!")
        retainedBuffer.ensureBufferLocked()
        return pixelBuffer.copyToByteArray()
    }

    override fun getNativeBuffer(): NativeBuffer {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Frame does not contain a Pixel Buffer!")
        return pixelBuffer.asNativeBuffer()
    }

    override fun convertCameraPointToFramePoint(cameraPoint: Point): Point {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Frame has already been disposed!")
        val matrix = FrameCoordinateSystemConverter.getCameraToFrameMatrix(pixelBuffer, orientation, isMirrored)
        return cameraPoint.applying(matrix)
    }

    override fun convertFramePointToCameraPoint(framePoint: Point): Point {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This Frame has already been disposed!")
        val matrix = FrameCoordinateSystemConverter.getFrameToCameraMatrix(pixelBuffer, orientation, isMirrored)
        return framePoint.applying(matrix)
    }

    override fun toString(): String = "Frame(${width}x$height, $pixelFormat, $orientation)"
}

/**
 * A plane of an [IosFrame] (port of V5's `HybridFramePlane.swift`). It keeps its [frame] alive, and is invalidated
 * when the frame is disposed.
 */
internal class IosFramePlane(
    private val frame: IosFrame,
    private val planeIndex: Int,
) : FramePlane {
    private var isDisposed = false

    private val pixelBuffer: CVPixelBufferRef?
        get() = if (isDisposed) null else frame.pixelBuffer

    override val width: Int
        get() = pixelBuffer?.let { CVPixelBufferGetWidthOfPlane(it, planeIndex.toULong()).toInt() } ?: 0

    override val height: Int
        get() = pixelBuffer?.let { CVPixelBufferGetHeightOfPlane(it, planeIndex.toULong()).toInt() } ?: 0

    override val bytesPerRow: Int
        get() = pixelBuffer?.let { CVPixelBufferGetBytesPerRowOfPlane(it, planeIndex.toULong()).toInt() } ?: 0

    override val isValid: Boolean get() = pixelBuffer != null

    /** Invalidates this plane, called when its [frame] is disposed. */
    fun dispose() {
        isDisposed = true
    }

    override fun getPixelBuffer(): ByteArray {
        val pixelBuffer = pixelBuffer ?: throw VisionCameraException("This FramePlane has already been disposed!")
        return pixelBuffer.copyPlaneToByteArray(planeIndex)
    }

    override fun toString(): String = "FramePlane(#$planeIndex, ${width}x$height)"
}
