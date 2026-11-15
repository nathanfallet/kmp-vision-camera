package me.nathanfallet.visioncamera.barcode

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import kotlinx.coroutines.tasks.await
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image

/**
 * The Android [BarcodeScanner], backed by an ML Kit barcode scanner (port of V5's `HybridBarcodeScanner`).
 *
 * The coordinates of the barcodes found in a [Frame] are in the Frame's raw (un-rotated) buffer, as documented and
 * as on iOS, so that [Frame.convertFramePointToCameraPoint] converts them to Camera coordinates (V5 reports them in
 * the upright image ML Kit scanned, after the Frame's `rotationDegrees`).
 */
internal class AndroidBarcodeScanner(
    options: BarcodeScannerOptions,
) : BarcodeScanner {
    private val scanner = BarcodeScanning.getClient(mlBarcodeScannerOptions(options.barcodeFormats))

    override fun scanCodes(frame: Frame): List<Barcode> {
        val input = frame.toMLKitInput()
        // Blocks the calling thread (the Frame's thread) until ML Kit is done, like V5.
        val barcodes = Tasks.await(scanner.process(input.image))
        return barcodes.map { AndroidBarcode(it, input.space) }
    }

    override suspend fun scanCodesAsync(frame: Frame): List<Barcode> = process(frame.toMLKitInput())

    override suspend fun scanCodesInImageAsync(image: Image): List<Barcode> = process(image.toMLKitInput())

    private suspend fun process(input: MLKitInput): List<Barcode> =
        scanner.process(input.image).await().map { AndroidBarcode(it, input.space) }

    override fun dispose() {
        scanner.close()
    }
}
