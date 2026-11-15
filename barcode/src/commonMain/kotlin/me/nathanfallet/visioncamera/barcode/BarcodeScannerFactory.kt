package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.CameraSession
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image

/**
 * Create a new [BarcodeScanner].
 *
 * The [BarcodeScanner] can be used to scan [Barcode]s in a [Frame] or an existing [Image].
 *
 * ```kotlin
 * val scanner = createBarcodeScanner(BarcodeScannerOptions(listOf(TargetBarcodeFormat.QR_CODE)))
 * ```
 */
fun createBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner = createPlatformBarcodeScanner(options)

/**
 * Create a new Barcode Scanner [CameraOutput].
 *
 * The Barcode Scanner [CameraOutput] can be attached to a [CameraSession], and calls
 * [BarcodeScannerOutputOptions.onBarcodeScanned] with the [Barcode]s detected in the Camera feed.
 *
 * ```kotlin
 * val barcodeOutput = createBarcodeScannerOutput(
 *     BarcodeScannerOutputOptions(
 *         barcodeFormats = listOf(TargetBarcodeFormat.ALL_FORMATS),
 *         onBarcodeScanned = { barcodes -> barcodes.forEach { println("Barcode value: ${it.rawValue}") } },
 *         onError = { error -> println("Failed to scan barcodes: $error") },
 *     ),
 * )
 * ```
 */
fun createBarcodeScannerOutput(options: BarcodeScannerOutputOptions): CameraOutput =
    createPlatformBarcodeScannerOutput(options)

/**
 * Creates the [BarcodeScanner] of the current platform.
 */
internal expect fun createPlatformBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner

/**
 * Creates the Barcode Scanner [CameraOutput] of the current platform.
 */
internal expect fun createPlatformBarcodeScannerOutput(options: BarcodeScannerOutputOptions): CameraOutput
