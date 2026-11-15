package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.CameraOutput

// Port of V5's HybridBarcodeScannerFactory.swift.

internal actual fun createPlatformBarcodeScanner(options: BarcodeScannerOptions): BarcodeScanner =
    IosBarcodeScanner(options)

internal actual fun createPlatformBarcodeScannerOutput(options: BarcodeScannerOutputOptions): CameraOutput =
    IosBarcodeScannerOutput(options)
