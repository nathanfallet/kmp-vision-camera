package me.nathanfallet.visioncamera.barcode

/**
 * Represents a detected [Barcode]'s format, or [UNKNOWN] if the format of a scanned Barcode could not be determined.
 */
enum class BarcodeFormat {
    /** The format of the scanned Barcode could not be determined. */
    UNKNOWN,

    /** A Code 128 barcode. */
    CODE_128,

    /** A Code 39 barcode. */
    CODE_39,

    /** A Code 93 barcode. */
    CODE_93,

    /** A Codabar barcode. */
    CODABAR,

    /** A Data Matrix code. */
    DATA_MATRIX,

    /** An EAN-13 barcode. */
    EAN_13,

    /** An EAN-8 barcode. */
    EAN_8,

    /** An ITF (Interleaved 2 of 5) barcode. */
    ITF,

    /** A QR code. */
    QR_CODE,

    /** A UPC-A barcode. */
    UPC_A,

    /** A UPC-E barcode. */
    UPC_E,

    /** A PDF417 code. */
    PDF_417,

    /** An Aztec code. */
    AZTEC,
}

/**
 * Represents a target [BarcodeFormat] that the [BarcodeScanner] can be configured to scan for, or [ALL_FORMATS] to
 * scan for every supported format.
 */
enum class TargetBarcodeFormat {
    /** A Code 128 barcode. */
    CODE_128,

    /** A Code 39 barcode. */
    CODE_39,

    /** A Code 93 barcode. */
    CODE_93,

    /** A Codabar barcode. */
    CODABAR,

    /** A Data Matrix code. */
    DATA_MATRIX,

    /** An EAN-13 barcode. */
    EAN_13,

    /** An EAN-8 barcode. */
    EAN_8,

    /** An ITF (Interleaved 2 of 5) barcode. */
    ITF,

    /** A QR code. */
    QR_CODE,

    /** A UPC-A barcode. */
    UPC_A,

    /** A UPC-E barcode. */
    UPC_E,

    /** A PDF417 code. */
    PDF_417,

    /** An Aztec code. */
    AZTEC,

    /** Every supported format. */
    ALL_FORMATS,
}
