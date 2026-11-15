package me.nathanfallet.visioncamera.barcode.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image
import me.nathanfallet.visioncamera.barcode.Barcode
import me.nathanfallet.visioncamera.barcode.BarcodeScanner
import me.nathanfallet.visioncamera.barcode.BarcodeScannerOptions
import me.nathanfallet.visioncamera.barcode.TargetBarcodeFormat
import me.nathanfallet.visioncamera.barcode.createBarcodeScanner

/**
 * Use a [BarcodeScanner] (the equivalent of VisionCamera's `useBarcodeScanner`).
 *
 * A [BarcodeScanner] can be used to detect [Barcode]s in a [Frame] in a Frame Processor, or in an existing [Image].
 * It is kept across recompositions, recreated when the [BarcodeScannerOptions.barcodeFormats] change, and disposed
 * once it leaves the composition (where V5 lets the garbage collector release it).
 *
 * ```kotlin
 * val barcodeScanner = rememberBarcodeScanner(BarcodeScannerOptions(listOf(TargetBarcodeFormat.ALL_FORMATS)))
 * val frameOutput = rememberFrameOutput { frame ->
 *     frame.use {
 *         val barcodes = barcodeScanner.scanCodes(it)
 *         println("Detected ${barcodes.size} barcodes!")
 *     }
 * }
 * ```
 *
 * @param options The formats to scan for.
 */
@Composable
fun rememberBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner =
    rememberBarcodeScanner(options.barcodeFormats)

/**
 * Use a [BarcodeScanner] scanning for the given [barcodeFormats] (the equivalent of VisionCamera's
 * `useBarcodeScanner({ barcodeFormats })`).
 *
 * A [BarcodeScanner] can be used to detect [Barcode]s in a [Frame] in a Frame Processor, or in an existing [Image].
 * It is kept across recompositions, recreated when the [barcodeFormats] change, and disposed once it leaves the
 * composition (where V5 lets the garbage collector release it).
 *
 * ```kotlin
 * val barcodeScanner = rememberBarcodeScanner(listOf(TargetBarcodeFormat.ALL_FORMATS))
 * val frameOutput = rememberFrameOutput { frame ->
 *     frame.use {
 *         val barcodes = barcodeScanner.scanCodes(it)
 *         println("Detected ${barcodes.size} barcodes!")
 *     }
 * }
 * ```
 *
 * @param barcodeFormats The formats to scan for. To detect all kinds of barcodes, use
 * `listOf(TargetBarcodeFormat.ALL_FORMATS)`.
 */
@Composable
fun rememberBarcodeScanner(barcodeFormats: List<TargetBarcodeFormat>): BarcodeScanner =
    remember(barcodeFormats) {
        RememberedDisposable(createBarcodeScanner(BarcodeScannerOptions(barcodeFormats))) { it.dispose() }
    }.value
