package me.nathanfallet.visioncamera

/**
 * A point used to focus AE/AF/AWB on the Camera via [CameraController.focusTo].
 *
 * Create one from a tap on a `PreviewView` (`previewView.createMeteringPoint(viewX, viewY)`), or from normalized
 * coordinates with [CameraFactory.createNormalizedMeteringPoint].
 *
 * ```kotlin
 * val point = VisionCamera.createNormalizedMeteringPoint(0.5, 0.5)
 * controller.focusTo(point)
 * ```
 */
interface MeteringPoint {
    /**
     * The X coordinate, relative to the source this point was created from: the view X coordinate for a
     * `PreviewView`, or the normalized X coordinate (`0.0` to `1.0`) for a normalized point.
     */
    val relativeX: Double

    /**
     * The Y coordinate, relative to the source this point was created from: the view Y coordinate for a
     * `PreviewView`, or the normalized Y coordinate (`0.0` to `1.0`) for a normalized point.
     */
    val relativeY: Double

    /** The size of this point, relative to the source it was created from, or `null` if none was set. */
    val relativeSize: Double?

    /** The X coordinate, normalized to the Camera's coordinate system after orientation, cropping and scaling. */
    val normalizedX: Double

    /** The Y coordinate, normalized to the Camera's coordinate system after orientation, cropping and scaling. */
    val normalizedY: Double

    /** The size of this point, normalized to the Camera's coordinate system. */
    val normalizedSize: Double
}
