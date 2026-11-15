package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.CameraOutput
import me.nathanfallet.visioncamera.VisionCameraException

@Suppress("UnusedParameter")
internal actual fun createPlatformBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner =
    throw VisionCameraException("The BarcodeScanner is not implemented yet on Android.")

@Suppress("UnusedParameter")
internal actual fun createPlatformBarcodeScannerOutput(options: BarcodeScannerOutputOptions): CameraOutput =
    throw VisionCameraException("The Barcode Scanner output is not implemented yet on Android.")
