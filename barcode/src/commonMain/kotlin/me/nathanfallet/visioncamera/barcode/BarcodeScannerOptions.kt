package me.nathanfallet.visioncamera.barcode

/**
 * Configuration options for a [BarcodeScanner].
 *
 * @see createBarcodeScanner
 */
data class BarcodeScannerOptions(
    /**
     * Specifies the formats to be used for Barcode scanning.
     *
     * If you want to detect all kinds of barcodes, use `listOf(TargetBarcodeFormat.ALL_FORMATS)`.
     */
    val barcodeFormats: List<TargetBarcodeFormat>,
)

/**
 * Configuration options for a Barcode Scanner output.
 *
 * @see createBarcodeScannerOutput
 */
data class BarcodeScannerOutputOptions(
    /**
     * Specifies the formats to be used for Barcode scanning.
     *
     * If you want to detect all kinds of barcodes, use `listOf(TargetBarcodeFormat.ALL_FORMATS)`.
     */
    val barcodeFormats: List<TargetBarcodeFormat>,
    /**
     * Controls which camera buffer resolution should be used: [BarcodeScannerOutputResolution.PREVIEW] (the default)
     * prefers preview-sized buffers for lower latency, [BarcodeScannerOutputResolution.FULL] prefers full/highest
     * available buffers for better detail.
     */
    val outputResolution: BarcodeScannerOutputResolution = BarcodeScannerOutputResolution.PREVIEW,
    /** Called whenever barcodes have been detected. */
    val onBarcodeScanned: (barcodes: List<Barcode>) -> Unit,
    /** Called when there was an error detecting barcodes. */
    val onError: (error: Throwable) -> Unit,
)
