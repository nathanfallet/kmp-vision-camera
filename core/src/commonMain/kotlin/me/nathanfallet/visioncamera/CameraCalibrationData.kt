package me.nathanfallet.visioncamera

/**
 * Per-frame calibration data, to counter-apply the transformations of the Camera/ISP: undo distortion correction, or
 * compute the scene in 3D space. iOS only.
 *
 * @see Depth.cameraCalibrationData
 * @see Photo.calibrationData
 */
data class CameraCalibrationData(
    /** The size of one pixel at [intrinsicMatrixReferenceDimensions], in millimeters. */
    val pixelSize: Double,
    /** The reference frame dimensions used to compute the camera's principal point. */
    val intrinsicMatrixReferenceDimensions: Size,
    /** The offset of the lens' distortion center from the top left, in [intrinsicMatrixReferenceDimensions]. */
    val lensDistortionCenter: Point,
    /**
     * The camera intrinsic matrix, mapping 3D camera coordinates to 2D pixel coordinates: a 3x3 matrix in column-major
     * order, with its origin at the top-left of the frame.
     * ```
     * K = [ fx  0 cx ]
     *     [  0 fy cy ]
     *     [  0  0  1 ]
     * ```
     * `fx` and `fy` are the focal length in pixels, `cx` and `cy` the principal point in pixels.
     */
    val cameraIntrinsicMatrix: List<Double>,
    /**
     * The camera extrinsic matrix, mapping world space to camera space: a 3x4 matrix in column-major order.
     * ```
     * [ r11 r12 r13 tx ]
     * [ r21 r22 r23 ty ]
     * [ r31 r32 r33 tz ]
     * ```
     * `r` is the rotation, `t` the translation in millimeters.
     */
    val cameraExtrinsicsMatrix: List<Double>,
)
