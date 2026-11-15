package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Point
import com.google.mlkit.vision.barcode.common.Barcode as MLBarcode

/**
 * The Android [Barcode], wrapping an ML Kit [MLBarcode] (port of V5's `HybridBarcode`).
 *
 * ML Kit reports the coordinates relative to the upright `InputImage` it scanned, i.e. after applying the rotation
 * the image was given (the Frame's `rotationDegrees`, or `0` for an [me.nathanfallet.visioncamera.Image]).
 */
internal class AndroidBarcode(
    private val barcode: MLBarcode,
) : Barcode {
    override val format: BarcodeFormat
        get() = barcodeFormatFromMLBarcodeFormat(barcode.format)

    override val boundingBox: Rect
        get() {
            val box = barcode.boundingBox ?: return Rect(0.0, 0.0, 0.0, 0.0)
            return Rect(box.left.toDouble(), box.right.toDouble(), box.top.toDouble(), box.bottom.toDouble())
        }

    override val cornerPoints: List<Point>
        get() = barcode.cornerPoints?.map { Point(it.x.toDouble(), it.y.toDouble()) } ?: emptyList()

    override val displayValue: String?
        get() = barcode.displayValue

    override val rawBytes: ByteArray? by lazy {
        barcode.rawBytes?.copyOf()
    }

    override val rawValue: String?
        get() = barcode.rawValue

    override val valueType: BarcodeValueType
        get() = barcodeValueTypeFromMLBarcodeValueType(barcode.valueType)

    override fun toString(): String = "Barcode($format, $rawValue)"
}
