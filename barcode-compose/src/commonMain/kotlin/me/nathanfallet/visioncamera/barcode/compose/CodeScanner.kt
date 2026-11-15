package me.nathanfallet.visioncamera.barcode.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraDeviceOrPosition
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.barcode.Barcode
import me.nathanfallet.visioncamera.barcode.BarcodeScanner
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOutputResolution
import me.nathanfallet.visioncamera.barcode.TargetBarcodeFormat
import me.nathanfallet.visioncamera.compose.Camera

/**
 * A view that detects [Barcode]s in a Camera, using the default back [CameraDevice] (the equivalent of
 * VisionCamera's `<CodeScanner />`).
 *
 * All [Barcode] coordinates are in the Frame's coordinate system. If you need to convert [Barcode] coordinates to
 * view coordinates, either use [rememberBarcodeScannerOutput] or [rememberBarcodeScanner] directly, and convert
 * coordinates using your preview view yourself. See [BarcodeScanner.scanCodes] for more information about
 * coordinate system conversions.
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
 *     val isActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
 *     CodeScanner(
 *         isActive = isActive,
 *         barcodeFormats = listOf(TargetBarcodeFormat.ALL_FORMATS),
 *         onBarcodeScanned = { barcodes -> println("Scanned ${barcodes.size} barcodes!") },
 *         onError = { error -> println("Error scanning barcodes: $error") },
 *         modifier = Modifier.fillMaxSize(),
 *     )
 * }
 * ```
 *
 * @param isActive Whether the [CodeScanner] is active, or not. You can toggle [isActive] to pause/resume the Camera
 * if the app becomes inactive, or the user navigates to a different screen.
 * @param barcodeFormats The formats to scan for. To detect all kinds of barcodes, use
 * `listOf(TargetBarcodeFormat.ALL_FORMATS)`.
 * @param onBarcodeScanned Called whenever barcodes have been detected.
 * @param onError Called when there was an error detecting barcodes, or when the Camera session encountered an error.
 * @param modifier The [Modifier] of this view. Most commonly, you would use `Modifier.fillMaxSize()`.
 * @param outputResolution Which camera buffer resolution is used: [BarcodeScannerOutputResolution.PREVIEW] prefers
 * preview-sized buffers for lower latency, [BarcodeScannerOutputResolution.FULL] full/highest available buffers for
 * better detail.
 * @param device The [CameraDevice] to scan with, or a [TargetCameraPosition] to use the default device at that
 * position. V5 always uses the default back device, which is the default here.
 */
@Suppress("LongParameterList")
@Composable
fun CodeScanner(
    isActive: Boolean,
    barcodeFormats: List<TargetBarcodeFormat>,
    onBarcodeScanned: (barcodes: List<Barcode>) -> Unit,
    onError: (error: Throwable) -> Unit,
    modifier: Modifier = Modifier,
    outputResolution: BarcodeScannerOutputResolution = BarcodeScannerOutputResolution.PREVIEW,
    device: CameraDeviceOrPosition = TargetCameraPosition.BACK,
) {
    val output = rememberBarcodeScannerOutput(
        barcodeFormats = barcodeFormats,
        outputResolution = outputResolution,
        onBarcodeScanned = onBarcodeScanned,
        onError = onError,
    )
    Camera(
        device = device,
        isActive = isActive,
        modifier = modifier,
        outputs = listOf(output),
        onError = onError,
    )
}
