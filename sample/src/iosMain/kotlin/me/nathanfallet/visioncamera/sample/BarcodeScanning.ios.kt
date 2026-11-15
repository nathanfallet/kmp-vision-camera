package me.nathanfallet.visioncamera.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputResolution

internal actual val barcodeOutputResolution: BarcodeScannerOutputResolution = BarcodeScannerOutputResolution.FULL

/**
 * On iOS, camera coordinates are normalized (`[0, 1]²`) in the sensor's native orientation, which is the orientation
 * of the output's buffers (of the output's `currentResolution` with [BarcodeScannerOutputResolution.FULL]): the
 * conversion is a normalization (`Frame.convertFramePointToCameraPoint` of an un-rotated, un-mirrored Frame).
 */
@Composable
internal actual fun rememberBarcodeOutputPointConverter(
    output: CameraOutput,
    device: CameraDevice?,
): ((Point) -> Point?)? = remember(output) {
    { point ->
        output.currentResolution?.let { resolution ->
            Point(point.x / resolution.width, point.y / resolution.height)
        }
    }
}
