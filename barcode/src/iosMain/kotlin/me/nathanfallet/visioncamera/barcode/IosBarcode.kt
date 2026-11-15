package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.barcode.parser.ParsedBarcodeValue

/**
 * The iOS [Barcode], read from a Vision `VNBarcodeObservation` by [VisionBarcodeDetector] (port of V5's
 * `HybridBarcode.swift`, which wraps an ML Kit `Barcode`). Its [valueType] and [displayValue] come from the
 * [parsedValue] of its [rawValue].
 */
internal class IosBarcode(
    override val format: BarcodeFormat,
    override val boundingBox: Rect,
    override val cornerPoints: List<Point>,
    override val rawBytes: ByteArray?,
    override val rawValue: String?,
    private val parsedValue: ParsedBarcodeValue,
) : Barcode {
    override val displayValue: String? get() = parsedValue.displayValue
    override val valueType: BarcodeValueType get() = parsedValue.valueType

    override fun toString(): String = "Barcode($format, $valueType, $rawValue)"
}
