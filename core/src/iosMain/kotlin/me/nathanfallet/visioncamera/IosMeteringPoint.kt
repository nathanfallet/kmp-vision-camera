package me.nathanfallet.visioncamera

/**
 * The iOS [MeteringPoint] (port of V5's `HybridMeteringPoint.swift`). Its normalized coordinates are in the
 * `AVCaptureDevice` point-of-interest coordinate system.
 */
internal class IosMeteringPoint private constructor(
    override val relativeX: Double,
    override val relativeY: Double,
    override val relativeSize: Double?,
    override val normalizedX: Double,
    override val normalizedY: Double,
    override val normalizedSize: Double,
) : MeteringPoint {
    override fun toString(): String = "MeteringPoint($normalizedX, $normalizedY, size: $normalizedSize)"

    companion object {
        /** The default normalized size of a [MeteringPoint]. */
        const val DEFAULT_SIZE = 0.1

        /** Creates a [MeteringPoint] from normalized coordinates, which are also its relative coordinates. */
        fun normalized(x: Double, y: Double, size: Double): IosMeteringPoint =
            IosMeteringPoint(
                relativeX = x,
                relativeY = y,
                relativeSize = size,
                normalizedX = x,
                normalizedY = y,
                normalizedSize = size,
            )

        /**
         * Creates a [MeteringPoint] from coordinates relative to a view, and their converted normalized
         * coordinates.
         */
        fun relative(
            relativeX: Double,
            relativeY: Double,
            relativeSize: Double?,
            normalizedX: Double,
            normalizedY: Double,
        ): IosMeteringPoint {
            val normalizedSize = if (relativeSize != null) {
                val fullX = relativeX / normalizedX
                relativeSize / fullX
            } else {
                DEFAULT_SIZE
            }
            return IosMeteringPoint(
                relativeX = relativeX,
                relativeY = relativeY,
                relativeSize = relativeSize,
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                normalizedSize = normalizedSize,
            )
        }
    }
}
