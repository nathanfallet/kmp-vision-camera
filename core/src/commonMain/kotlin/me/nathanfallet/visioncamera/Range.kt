package me.nathanfallet.visioncamera

/**
 * Represents a numeric range with a minimum and maximum value, inclusive on both ends.
 */
data class Range(
    /** The lower bound of the range, inclusive. */
    val min: Double,
    /** The upper bound of the range, inclusive. */
    val max: Double,
) {
    /**
     * Whether the given [value] is within this range.
     */
    operator fun contains(value: Double): Boolean = value in min..max
}
