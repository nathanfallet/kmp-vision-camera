package me.nathanfallet.visioncamera.barcode

/**
 * Represents a Rectangle in the current context's coordinate system.
 */
data class Rect(
    /** The left value (min X) of the Rectangle. */
    val left: Double,
    /** The right value (max X) of the Rectangle. */
    val right: Double,
    /** The top value (min Y) of the Rectangle. */
    val top: Double,
    /** The bottom value (max Y) of the Rectangle. */
    val bottom: Double,
)
