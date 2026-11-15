package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.CameraOutput

internal actual fun createPlatformBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner =
    AndroidBarcodeScanner(options)

internal actual fun createPlatformBarcodeScannerOutput(options: BarcodeScannerOutputOptions): CameraOutput =
    AndroidBarcodeScannerOutput(options)
