package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.VisionCameraException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions as MLBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode as MLBarcode

/**
 * Converts this [TargetBarcodeFormat] to its ML Kit `Barcode.FORMAT_*` value (V5's `ML+BarcodeFormat.kt`).
 */
@Suppress("CyclomaticComplexMethod")
internal fun TargetBarcodeFormat.toMLBarcodeFormat(): Int = when (this) {
    TargetBarcodeFormat.CODE_128 -> MLBarcode.FORMAT_CODE_128
    TargetBarcodeFormat.CODE_39 -> MLBarcode.FORMAT_CODE_39
    TargetBarcodeFormat.CODE_93 -> MLBarcode.FORMAT_CODE_93
    TargetBarcodeFormat.CODABAR -> MLBarcode.FORMAT_CODABAR
    TargetBarcodeFormat.DATA_MATRIX -> MLBarcode.FORMAT_DATA_MATRIX
    TargetBarcodeFormat.EAN_13 -> MLBarcode.FORMAT_EAN_13
    TargetBarcodeFormat.EAN_8 -> MLBarcode.FORMAT_EAN_8
    TargetBarcodeFormat.ITF -> MLBarcode.FORMAT_ITF
    TargetBarcodeFormat.QR_CODE -> MLBarcode.FORMAT_QR_CODE
    TargetBarcodeFormat.UPC_A -> MLBarcode.FORMAT_UPC_A
    TargetBarcodeFormat.UPC_E -> MLBarcode.FORMAT_UPC_E
    TargetBarcodeFormat.PDF_417 -> MLBarcode.FORMAT_PDF417
    TargetBarcodeFormat.AZTEC -> MLBarcode.FORMAT_AZTEC
    TargetBarcodeFormat.ALL_FORMATS -> MLBarcode.FORMAT_ALL_FORMATS
}

/**
 * Converts an ML Kit `Barcode.FORMAT_*` value to a [BarcodeFormat] (V5's `ML+BarcodeFormat.kt`).
 */
@Suppress("CyclomaticComplexMethod")
internal fun barcodeFormatFromMLBarcodeFormat(format: Int): BarcodeFormat = when (format) {
    MLBarcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    MLBarcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    MLBarcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    MLBarcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    MLBarcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    MLBarcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    MLBarcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    MLBarcode.FORMAT_ITF -> BarcodeFormat.ITF
    MLBarcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    MLBarcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    MLBarcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    MLBarcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    MLBarcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    else -> BarcodeFormat.UNKNOWN
}

/**
 * Converts an ML Kit `Barcode.TYPE_*` value to a [BarcodeValueType] (V5's `ML+BarcodeValueType.kt`).
 */
@Suppress("CyclomaticComplexMethod")
internal fun barcodeValueTypeFromMLBarcodeValueType(type: Int): BarcodeValueType = when (type) {
    MLBarcode.TYPE_CONTACT_INFO -> BarcodeValueType.CONTACT_INFO
    MLBarcode.TYPE_EMAIL -> BarcodeValueType.EMAIL
    MLBarcode.TYPE_ISBN -> BarcodeValueType.ISBN
    MLBarcode.TYPE_PHONE -> BarcodeValueType.PHONE
    MLBarcode.TYPE_PRODUCT -> BarcodeValueType.PRODUCT
    MLBarcode.TYPE_SMS -> BarcodeValueType.SMS
    MLBarcode.TYPE_TEXT -> BarcodeValueType.TEXT
    MLBarcode.TYPE_URL -> BarcodeValueType.URL
    MLBarcode.TYPE_WIFI -> BarcodeValueType.WIFI
    MLBarcode.TYPE_GEO -> BarcodeValueType.GEO
    MLBarcode.TYPE_CALENDAR_EVENT -> BarcodeValueType.CALENDAR_EVENT
    MLBarcode.TYPE_DRIVER_LICENSE -> BarcodeValueType.DRIVER_LICENSE
    else -> BarcodeValueType.UNKNOWN
}

/**
 * Creates the ML Kit [MLBarcodeScannerOptions] scanning for the given [barcodeFormats]
 * (V5's `BarcodeScannerOptions+toMLBarcodeScannerOptions.kt`).
 *
 * @throws VisionCameraException If [barcodeFormats] is empty.
 */
// ML Kit's `setBarcodeFormats(int, int...)` is a varargs API: the spread copies a few ints, once per scanner.
@Suppress("SpreadOperator")
internal fun mlBarcodeScannerOptions(barcodeFormats: List<TargetBarcodeFormat>): MLBarcodeScannerOptions {
    val formats = barcodeFormats.map { it.toMLBarcodeFormat() }
    if (formats.isEmpty()) {
        throw VisionCameraException("Target barcodeFormats cannot be empty!")
    }
    return MLBarcodeScannerOptions.Builder()
        .setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
        .build()
}
