package me.nathanfallet.visioncamera

import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android [Depth], wrapping a CameraX [ImageProxy] in [android.graphics.ImageFormat.DEPTH16].
 */
internal class AndroidDepth(
    override val image: ImageProxy,
    override val orientation: CameraOrientation,
    override val isMirrored: Boolean,
) : Depth, NativeFrame {
    private val isDisposed = AtomicBoolean(false)

    override val timestamp: Long
        get() = image.imageInfo.timestamp

    override val width: Int
        get() = if (isValid) image.width else 0

    override val height: Int
        get() = if (isValid) image.height else 0

    override val bytesPerRow: Int
        get() = image.planes.singleOrNull()?.rowStride ?: 0

    override val pixelFormat: DepthPixelFormat
        get() = image.depthPixelFormat

    override val isValid: Boolean
        get() = try {
            // accessing format on a Closed Image might throw
            image.format
            true
        } catch (_: IllegalStateException) {
            false
        }

    override val isDepthDataFiltered: Boolean
        get() = false

    override val depthDataAccuracy: DepthDataAccuracy
        get() = DepthDataAccuracy.ABSOLUTE

    override val depthDataQuality: DepthDataQuality
        get() = DepthDataQuality.UNKNOWN

    override val availableDepthPixelFormats: List<DepthPixelFormat>
        get() = emptyList()

    // Limitation: Get CameraCalibrationData somehow?
    override val cameraCalibrationData: CameraCalibrationData?
        get() = null

    override fun getDepthData(): ByteArray {
        if (!isValid) throw VisionCameraException("This Depth is not valid anymore, it has been disposed!")
        return image.getPixelBytes()
    }

    override fun getNativeBuffer(): NativeBuffer = image.getNativeBuffer()

    override fun rotate(orientation: CameraOrientation, isMirrored: Boolean): Depth {
        // Limitation: On Android this is simply not supported.
        //       We need to specify the rotation upfront -
        //       maybe time to re-design the API?
        throw VisionCameraException("Rotating Depth Buffers is not supported!")
    }

    override fun convert(pixelFormat: DepthPixelFormat): Depth {
        // Limitation: On Android this is simply not supported.
        //       We need to specify the Pixel Format upfront when creating the ImageReader -
        //       maybe time to re-design the API?
        throw VisionCameraException("Depth Data conversion to $pixelFormat is not supported!")
    }

    override fun convertCameraPointToDepthPoint(cameraPoint: Point): Point =
        image.imageInfo.sensorToBufferTransformMatrix.convertPoint(cameraPoint)

    override fun convertDepthPointToCameraPoint(depthPoint: Point): Point {
        val bufferToSensor = Matrix().apply {
            image.imageInfo.sensorToBufferTransformMatrix.invert(this)
        }
        return bufferToSensor.convertPoint(depthPoint)
    }

    // The Frame shares this Depth's image, so disposing either of them closes it, once.
    override fun toFrame(): Frame = AndroidFrame(image, orientation, isMirrored, isDisposed)

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) image.close()
    }
}
