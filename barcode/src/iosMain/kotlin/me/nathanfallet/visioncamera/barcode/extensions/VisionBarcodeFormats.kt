package me.nathanfallet.visioncamera.barcode.extensions

import me.nathanfallet.visioncamera.barcode.BarcodeFormat
import me.nathanfallet.visioncamera.barcode.TargetBarcodeFormat
import platform.Vision.VNBarcodeSymbology
import platform.Vision.VNBarcodeSymbologyAztec
import platform.Vision.VNBarcodeSymbologyCodabar
import platform.Vision.VNBarcodeSymbologyCode128
import platform.Vision.VNBarcodeSymbologyCode39
import platform.Vision.VNBarcodeSymbologyCode39Checksum
import platform.Vision.VNBarcodeSymbologyCode39FullASCII
import platform.Vision.VNBarcodeSymbologyCode39FullASCIIChecksum
import platform.Vision.VNBarcodeSymbologyCode93
import platform.Vision.VNBarcodeSymbologyCode93i
import platform.Vision.VNBarcodeSymbologyDataMatrix
import platform.Vision.VNBarcodeSymbologyEAN13
import platform.Vision.VNBarcodeSymbologyEAN8
import platform.Vision.VNBarcodeSymbologyI2of5
import platform.Vision.VNBarcodeSymbologyI2of5Checksum
import platform.Vision.VNBarcodeSymbologyITF14
import platform.Vision.VNBarcodeSymbologyPDF417
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNBarcodeSymbologyUPCE

// Port of V5's Extensions/ML+BarcodeFormat.swift and ML+BarcodeScannerOptions.swift, for Apple Vision.

/** The length of a UPC-A code. */
private const val UPC_A_LENGTH = 12

/**
 * The [BarcodeFormat]s a [TargetBarcodeFormat] scans for. [TargetBarcodeFormat.ALL_FORMATS] scans for every format
 * of V5 (ML Kit's formats); every other [TargetBarcodeFormat] scans for the [BarcodeFormat] of the same name.
 */
internal fun TargetBarcodeFormat.toBarcodeFormats(): List<BarcodeFormat> =
    if (this == TargetBarcodeFormat.ALL_FORMATS) {
        BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
    } else {
        listOf(BarcodeFormat.valueOf(name))
    }

/**
 * The Vision symbologies detecting this [BarcodeFormat]. Vision has no UPC-A symbology: it detects UPC-A codes as
 * EAN-13s with a leading `0` (see [resolveVisionFormat]).
 */
internal fun BarcodeFormat.toVisionSymbologies(): List<VNBarcodeSymbology> = when (this) {
    BarcodeFormat.UNKNOWN -> emptyList()
    BarcodeFormat.CODE_128 -> listOf(VNBarcodeSymbologyCode128)
    BarcodeFormat.CODE_39 -> listOf(
        VNBarcodeSymbologyCode39,
        VNBarcodeSymbologyCode39Checksum,
        VNBarcodeSymbologyCode39FullASCII,
        VNBarcodeSymbologyCode39FullASCIIChecksum,
    )
    BarcodeFormat.CODE_93 -> listOf(VNBarcodeSymbologyCode93, VNBarcodeSymbologyCode93i)
    BarcodeFormat.CODABAR -> listOf(VNBarcodeSymbologyCodabar)
    BarcodeFormat.DATA_MATRIX -> listOf(VNBarcodeSymbologyDataMatrix)
    BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> listOf(VNBarcodeSymbologyEAN13)
    BarcodeFormat.EAN_8 -> listOf(VNBarcodeSymbologyEAN8)
    BarcodeFormat.ITF -> listOf(VNBarcodeSymbologyI2of5, VNBarcodeSymbologyI2of5Checksum, VNBarcodeSymbologyITF14)
    BarcodeFormat.QR_CODE -> listOf(VNBarcodeSymbologyQR)
    BarcodeFormat.UPC_E -> listOf(VNBarcodeSymbologyUPCE)
    BarcodeFormat.PDF_417 -> listOf(VNBarcodeSymbologyPDF417)
    BarcodeFormat.AZTEC -> listOf(VNBarcodeSymbologyAztec)
}

/** Converts a Vision symbology to its [BarcodeFormat], or [BarcodeFormat.UNKNOWN] if V5 has no such format. */
@Suppress("CyclomaticComplexMethod")
internal fun barcodeFormatOfVisionSymbology(symbology: VNBarcodeSymbology): BarcodeFormat = when (symbology) {
    VNBarcodeSymbologyCode128 -> BarcodeFormat.CODE_128
    VNBarcodeSymbologyCode39,
    VNBarcodeSymbologyCode39Checksum,
    VNBarcodeSymbologyCode39FullASCII,
    VNBarcodeSymbologyCode39FullASCIIChecksum,
    -> BarcodeFormat.CODE_39
    VNBarcodeSymbologyCode93, VNBarcodeSymbologyCode93i -> BarcodeFormat.CODE_93
    VNBarcodeSymbologyCodabar -> BarcodeFormat.CODABAR
    VNBarcodeSymbologyDataMatrix -> BarcodeFormat.DATA_MATRIX
    VNBarcodeSymbologyEAN13 -> BarcodeFormat.EAN_13
    VNBarcodeSymbologyEAN8 -> BarcodeFormat.EAN_8
    VNBarcodeSymbologyI2of5, VNBarcodeSymbologyI2of5Checksum, VNBarcodeSymbologyITF14 -> BarcodeFormat.ITF
    VNBarcodeSymbologyQR -> BarcodeFormat.QR_CODE
    VNBarcodeSymbologyUPCE -> BarcodeFormat.UPC_E
    VNBarcodeSymbologyPDF417 -> BarcodeFormat.PDF_417
    VNBarcodeSymbologyAztec -> BarcodeFormat.AZTEC
    else -> BarcodeFormat.UNKNOWN
}

/** The format and raw value of a detected barcode, as ML Kit reports them. */
internal data class ResolvedBarcodeFormat(
    /** The format of the barcode. */
    val format: BarcodeFormat,
    /** The raw value of the barcode. */
    val rawValue: String?,
)

/**
 * Resolves the [format] and [rawValue] Vision detected to the format and raw value ML Kit reports when scanning for
 * the [requestedFormats], or `null` if the barcode was not requested (and must be dropped).
 *
 * Vision detects UPC-A codes as EAN-13s with a leading `0` (UPC-A is a subset of EAN-13). ML Kit reports them as
 * [BarcodeFormat.UPC_A] with their 12 digits when UPC-A is requested, and as [BarcodeFormat.EAN_13] otherwise.
 */
internal fun resolveVisionFormat(
    format: BarcodeFormat,
    rawValue: String?,
    requestedFormats: Set<BarcodeFormat>,
): ResolvedBarcodeFormat? {
    val upcA = if (format == BarcodeFormat.EAN_13) rawValue?.upcAOfEan13() else null
    return when {
        upcA != null && BarcodeFormat.UPC_A in requestedFormats -> ResolvedBarcodeFormat(BarcodeFormat.UPC_A, upcA)
        format in requestedFormats -> ResolvedBarcodeFormat(format, rawValue)
        else -> null
    }
}

/** The 12-digit UPC-A code this 13-digit EAN-13 code is (with a leading `0`), or `null` if it is not a UPC-A. */
private fun String.upcAOfEan13(): String? {
    val isUpcA = length == UPC_A_LENGTH + 1 && startsWith('0') && all { it.isDigit() }
    return if (isUpcA) drop(1) else null
}
