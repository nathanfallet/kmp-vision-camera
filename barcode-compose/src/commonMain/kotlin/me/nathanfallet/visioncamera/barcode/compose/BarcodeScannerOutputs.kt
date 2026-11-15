package me.nathanfallet.visioncamera.barcode.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.CameraSession
import me.nathanfallet.visioncamera.barcode.Barcode
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputOptions
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputResolution
import me.nathanfallet.visioncamera.barcode.TargetBarcodeFormat
import me.nathanfallet.visioncamera.barcode.createBarcodeScannerOutput

/**
 * Use a Barcode Scanner [CameraOutput] (the equivalent of VisionCamera's `useBarcodeScannerOutput`).
 *
 * The Barcode Scanner [CameraOutput] can be attached to a [CameraSession] (e.g. with
 * [me.nathanfallet.visioncamera.compose.rememberCamera]) or to a [me.nathanfallet.visioncamera.compose.Camera]
 * composable. It is kept across recompositions and recreated when the [barcodeFormats] or the [outputResolution]
 * change, while the latest [onBarcodeScanned] and [onError] are always called, without recreating the output.
 *
 * Attach to a `Camera` composable:
 * ```kotlin
 * val scannerOutput = rememberBarcodeScannerOutput(
 *     barcodeFormats = listOf(TargetBarcodeFormat.ALL_FORMATS),
 *     onBarcodeScanned = { barcodes -> println("Scanned ${barcodes.size} barcodes!") },
 *     onError = { error -> println("Failed to scan barcodes! $error") },
 * )
 * Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(scannerOutput))
 * ```
 *
 * Attach to a `CameraSession`:
 * ```kotlin
 * val scannerOutput = rememberBarcodeScannerOutput(
 *     barcodeFormats = listOf(TargetBarcodeFormat.ALL_FORMATS),
 *     onBarcodeScanned = { barcodes -> println("Scanned ${barcodes.size} barcodes!") },
 *     onError = { error -> println("Failed to scan barcodes! $error") },
 * )
 * val controller = rememberCamera(isActive = true, device = TargetCameraPosition.BACK, outputs = listOf(scannerOutput))
 * ```
 *
 * All [Barcode] coordinates are in the coordinate system of the Frame they were detected in (its raw buffer). For
 * full control over coordinate system conversions (`Frame.convertFramePointToCameraPoint`, then the preview's
 * `convertCameraPointToViewPoint`), scan Frames yourself with [rememberBarcodeScanner] in a Frame Processor.
 *
 * @param barcodeFormats The formats to scan for. To detect all kinds of barcodes, use
 * `listOf(TargetBarcodeFormat.ALL_FORMATS)`.
 * @param outputResolution Which camera buffer resolution is used: [BarcodeScannerOutputResolution.PREVIEW] prefers
 * preview-sized buffers for lower latency, [BarcodeScannerOutputResolution.FULL] full/highest available buffers for
 * better detail.
 * @param onBarcodeScanned Called whenever barcodes have been detected.
 * @param onError Called when there was an error detecting barcodes.
 */
@Composable
fun rememberBarcodeScannerOutput(
    barcodeFormats: List<TargetBarcodeFormat>,
    outputResolution: BarcodeScannerOutputResolution = BarcodeScannerOutputResolution.PREVIEW,
    onBarcodeScanned: (barcodes: List<Barcode>) -> Unit,
    onError: (error: Throwable) -> Unit,
): CameraOutput {
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val currentOnError by rememberUpdatedState(onError)
    return remember(barcodeFormats, outputResolution) {
        val output = createBarcodeScannerOutput(
            BarcodeScannerOutputOptions(
                barcodeFormats = barcodeFormats,
                outputResolution = outputResolution,
                onBarcodeScanned = { barcodes -> currentOnBarcodeScanned(barcodes) },
                onError = { error -> currentOnError(error) },
            ),
        )
        // Releases the output's native resources (Android's ML Kit scanner and analysis thread) once it is no longer
        // used, where V5 lets the garbage collector release it.
        RememberedDisposable(output) { (it as? AutoCloseable)?.close() }
    }.value
}
