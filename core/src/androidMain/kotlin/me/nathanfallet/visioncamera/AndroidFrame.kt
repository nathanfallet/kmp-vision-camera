package me.nathanfallet.visioncamera

import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android [Frame], wrapping a CameraX [ImageProxy].
 */
internal class AndroidFrame(
    override val image: ImageProxy,
    override val orientation: CameraOrientation,
    override val isMirrored: Boolean,
    // Shared with the Depth this Frame was created from (Depth.toFrame), since both wrap the same ImageProxy, which
    // must only be closed once.
    private val isDisposed: AtomicBoolean = AtomicBoolean(false),
) : Frame, NativeFrame {
    private var planesCached: List<FramePlane>? = null

    override val timestamp: Long
        get() = image.imageInfo.timestamp

    override val isValid: Boolean
        get() = try {
            // accessing format on a Closed Image might throw
            image.format
            true
        } catch (_: IllegalStateException) {
            false
        }

    override val width: Int
        get() = image.width

    override val height: Int
        get() = image.height

    override val bytesPerRow: Int
        get() = image.planes.singleOrNull()?.rowStride ?: 0

    override val pixelFormat: PixelFormat
        get() = image.pixelFormat

    override val isPlanar: Boolean
        get() = image.planes.size > 1

    override val hasPixelBuffer: Boolean
        get() = image.hasPixelBuffer

    override val hasNativeBuffer: Boolean
        get() = image.hasNativeBuffer

    // Limitation: Implement `cameraIntrinsicMatrix`
    override val cameraIntrinsicMatrix: List<Double>?
        get() = null

    override fun getNativeBuffer(): NativeBuffer = image.getNativeBuffer()

    override fun getPlanes(): List<FramePlane> {
        requireValid()
        if (!isPlanar) return emptyList()
        // we may have planes cached
        return planesCached ?: image.planes.map { AndroidFramePlane(it) }.also { planesCached = it }
    }

    override fun getPixelBuffer(): ByteArray {
        requireValid()
        return image.getPixelBytes()
    }

    override fun convertCameraPointToFramePoint(cameraPoint: Point): Point =
        image.imageInfo.sensorToBufferTransformMatrix.convertPoint(cameraPoint)

    override fun convertFramePointToCameraPoint(framePoint: Point): Point {
        val bufferToSensor = Matrix().apply {
            image.imageInfo.sensorToBufferTransformMatrix.invert(this)
        }
        return bufferToSensor.convertPoint(framePoint)
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            planesCached = null
            image.close()
        }
    }

    private fun requireValid() {
        if (!isValid) throw VisionCameraException("This Frame is not valid anymore, it has been disposed!")
    }
}
