package me.nathanfallet.visioncamera.barcode

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image

/**
 * The Android [BarcodeScanner], backed by an ML Kit barcode scanner (port of V5's `HybridBarcodeScanner`).
 */
internal class AndroidBarcodeScanner(
    options: BarcodeScannerOptions,
) : BarcodeScanner {
    private val scanner = BarcodeScanning.getClient(mlBarcodeScannerOptions(options.barcodeFormats))

    override fun scanCodes(frame: Frame): List<Barcode> {
        val inputImage = frame.toInputImage()
        // Blocks the calling thread (the Frame's thread) until ML Kit is done, like V5.
        val barcodes = Tasks.await(scanner.process(inputImage))
        return barcodes.map { AndroidBarcode(it) }
    }

    override suspend fun scanCodesAsync(frame: Frame): List<Barcode> = process(frame.toInputImage())

    override suspend fun scanCodesInImageAsync(image: Image): List<Barcode> = process(image.toInputImage())

    private suspend fun process(inputImage: InputImage): List<Barcode> =
        scanner.process(inputImage).await().map { AndroidBarcode(it) }

    override fun dispose() {
        scanner.close()
    }
}
