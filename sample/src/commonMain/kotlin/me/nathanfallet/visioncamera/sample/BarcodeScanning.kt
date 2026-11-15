package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.barcode.Barcode
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputResolution
import me.nathanfallet.visioncamera.barcode.TargetBarcodeFormat
import me.nathanfallet.visioncamera.barcode.compose.rememberBarcodeScannerOutput
import me.nathanfallet.visioncamera.compose.PreviewViewRef

/**
 * The barcode output's resolution. On iOS, [BarcodeScannerOutputResolution.FULL] makes the scanned buffers as large
 * as the output's `currentResolution`, which [rememberBarcodeOutputPointConverter] needs to normalize coordinates.
 */
internal expect val barcodeOutputResolution: BarcodeScannerOutputResolution

/**
 * Remembers a converter from a point of the [output]'s buffers (the coordinate system of the barcodes it scans) to
 * camera coordinates, as accepted by [PreviewViewRef.convertCameraPointToViewPoint], or returns `null` if it is not
 * available for this [device].
 *
 * This is the closest equivalent of `Frame.convertFramePointToCameraPoint` for the barcode output, which has no
 * `Frame`. The converter returns `null` while the output is not streaming.
 */
@Composable
internal expect fun rememberBarcodeOutputPointConverter(
    output: CameraOutput,
    device: CameraDevice?,
): ((Point) -> Point?)?

/** The state of the barcode scanner output: the barcodes in the last scanned frame, and the last barcode seen. */
internal class BarcodeScanState(
    /** The barcode scanner output. */
    val output: CameraOutput,
    /** The barcodes of the last scanned frame. */
    val barcodes: List<Barcode>,
    /** The last barcode scanned, kept until another one is scanned. */
    val lastBarcode: Barcode?,
    /** The last scanning error. */
    val error: String?,
)

/** A barcode scanner output detecting all formats, and its state. */
@Composable
internal fun rememberBarcodeScan(): BarcodeScanState {
    var barcodes by remember { mutableStateOf(emptyList<Barcode>()) }
    var lastBarcode by remember { mutableStateOf<Barcode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val output = rememberBarcodeScannerOutput(
        barcodeFormats = remember { listOf(TargetBarcodeFormat.ALL_FORMATS) },
        outputResolution = barcodeOutputResolution,
        onBarcodeScanned = { scanned ->
            scanned.forEach { println(it.toLogString()) }
            barcodes = scanned
            scanned.firstOrNull()?.let { lastBarcode = it }
            error = null
        },
        onError = { error = it.message ?: it.toString() },
    )
    return BarcodeScanState(output, barcodes, lastBarcode, error)
}

/** Everything about this barcode, on one line, to read it from the device logs (`VisionCameraSample` prefix). */
internal fun Barcode.toLogString(): String =
    "VisionCameraSample barcode: format=$format, rawValue=$rawValue, displayValue=$displayValue, " +
        "valueType=$valueType, boundingBox=$boundingBox, cornerPoints=$cornerPoints"

/** A one-line description of this barcode: its value, format and value type. */
internal fun Barcode.describe(): String = "${displayValue ?: rawValue ?: "?"} ($format, $valueType)"

/**
 * Outlines the [barcodes] on top of the preview: their corner points are converted to camera coordinates with
 * [toCameraPoint], then to view coordinates with the preview.
 */
@Composable
internal fun BarcodesOverlay(
    barcodes: List<Barcode>,
    toCameraPoint: ((Point) -> Point?)?,
    previewRef: PreviewViewRef,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (!previewRef.isReady || toCameraPoint == null) return@Canvas
        barcodes.forEach { barcode ->
            val points = barcode.cornerPoints.mapNotNull { point ->
                toCameraPoint(point)?.let { previewRef.convertCameraPointToViewPoint(it) }
            }
            if (points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Color.Cyan, style = Stroke(width = 4.dp.toPx()))
            points.forEach { drawCircle(Color.Yellow, radius = 6.dp.toPx(), center = it) }
        }
    }
}
