package me.nathanfallet.visioncamera.barcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image
import me.nathanfallet.visioncamera.barcode.extensions.toVisionInput
import me.nathanfallet.visioncamera.toUIImage

/**
 * The iOS [BarcodeScanner], scanning with Apple Vision (port of V5's `HybridBarcodeScanner.swift`, which scans with
 * ML Kit).
 */
internal class IosBarcodeScanner(options: BarcodeScannerOptions) : BarcodeScanner {
    private val detector = VisionBarcodeDetector(options.barcodeFormats)

    override fun scanCodes(frame: Frame): List<Barcode> {
        val input = frame.toVisionInput()
        return detector.detect(input.pixelBuffer, input.orientation)
    }

    override suspend fun scanCodesAsync(frame: Frame): List<Barcode> {
        val input = frame.toVisionInput()
        return withContext(Dispatchers.Default) { detector.detect(input.pixelBuffer, input.orientation) }
    }

    override suspend fun scanCodesInImageAsync(image: Image): List<Barcode> {
        val uiImage = image.toUIImage()
        return withContext(Dispatchers.Default) { detector.detect(uiImage) }
    }

    override fun dispose() {
        // Vision requests are created for each scan: there are no native resources to release.
    }

    override fun toString(): String = "BarcodeScanner()"
}
