package me.nathanfallet.visioncamera.sample

import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputResolution

internal actual val barcodeOutputResolution: BarcodeScannerOutputResolution = BarcodeScannerOutputResolution.PREVIEW

/**
 * On Android, camera coordinates are sensor coordinates (the active pixel array). Like CameraX's
 * `sensorToBufferTransformMatrix` (which `Frame.convertFramePointToCameraPoint` inverts), the output's buffer (of
 * the output's `currentResolution`, un-rotated) is the largest centered crop of the sensor with its aspect ratio.
 */
@Composable
internal actual fun rememberBarcodeOutputPointConverter(
    output: CameraOutput,
    device: CameraDevice?,
): ((Point) -> Point?)? {
    val context = LocalContext.current
    val cameraId = device?.id ?: return null
    val sensorRect = remember(cameraId) {
        runCatching {
            context.getSystemService(CameraManager::class.java)
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        }.getOrNull()
    } ?: return null
    return remember(output, sensorRect) {
        { point ->
            output.currentResolution?.let { resolution ->
                val bufferToSensor = Matrix().apply {
                    val bufferRect = RectF(0f, 0f, resolution.width.toFloat(), resolution.height.toFloat())
                    setRectToRect(bufferRect, RectF(sensorRect), Matrix.ScaleToFit.CENTER)
                }
                val coordinates = floatArrayOf(point.x.toFloat(), point.y.toFloat())
                bufferToSensor.mapPoints(coordinates)
                Point(coordinates[0].toDouble(), coordinates[1].toDouble())
            }
        }
    }
}
