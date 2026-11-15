package me.nathanfallet.visioncamera

import android.annotation.SuppressLint

/**
 * The Android [MeteringPoint], wrapping a CameraX [androidx.camera.core.MeteringPoint].
 */
internal class AndroidMeteringPoint(
    override val relativeX: Double,
    override val relativeY: Double,
    override val relativeSize: Double?,
    /** The CameraX metering point. */
    val meteringPoint: androidx.camera.core.MeteringPoint,
) : MeteringPoint {
    override val normalizedX: Double
        @SuppressLint("RestrictedApi")
        get() = meteringPoint.x.toDouble()

    override val normalizedY: Double
        @SuppressLint("RestrictedApi")
        get() = meteringPoint.y.toDouble()

    override val normalizedSize: Double get() = meteringPoint.size.toDouble()

    override fun toString(): String =
        "MeteringPoint(relativeX: $relativeX, relativeY: $relativeY, relativeSize: $relativeSize, " +
            "normalizedX: $normalizedX, normalizedY: $normalizedY, normalizedSize: $normalizedSize)"
}
