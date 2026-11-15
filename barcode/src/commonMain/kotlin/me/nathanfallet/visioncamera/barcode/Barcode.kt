package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image
import me.nathanfallet.visioncamera.Point

/**
 * Represents a single detected Barcode.
 *
 * A [Barcode] is produced by the [BarcodeScanner] or the Barcode Scanner output ([createBarcodeScannerOutput]).
 *
 * See [BarcodeScanner.scanCodes] and [BarcodeScanner.scanCodesInImageAsync] for information about Barcode
 * coordinate systems.
 *
 * ```kotlin
 * val barcode = ...
 * println("Barcode value: ${barcode.rawValue}")
 * ```
 */
interface Barcode {
    /** The format of this [Barcode]. */
    val format: BarcodeFormat

    /** The [Barcode]'s bounding box. The coordinates are relative to the input [Frame] or [Image]. */
    val boundingBox: Rect

    /** The [Barcode]'s corner points. The coordinates are relative to the input [Frame] or [Image]. */
    val cornerPoints: List<Point>

    /** The [Barcode]'s value in a user-friendly format. */
    val displayValue: String?

    /** The [Barcode]'s value in raw bytes. */
    val rawBytes: ByteArray?

    /** The [Barcode]'s raw value as a string. */
    val rawValue: String?

    /** The type of the [Barcode]'s value. */
    val valueType: BarcodeValueType
}
