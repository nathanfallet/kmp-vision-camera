package me.nathanfallet.visioncamera

import cnames.structs.__CFData
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCameraCalibrationData
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFGetTypeID
import platform.CoreMedia.CMGetAttachment
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.kCMSampleBufferAttachmentKey_CameraIntrinsicMatrix
import platform.darwin.simd_float3x3
import platform.darwin.simd_float4x3

// Port of V5's HybridCameraCalibrationData.swift, simd_float+toDoubleArray.swift and
// CMSampleBuffer+getCameraIntrinsicMatrix.swift.

/** The number of rows of the 3x3 and 4x3 `simd` matrices. */
private const val MATRIX_ROWS = 3

/** The number of columns of a 3x3 `simd` matrix. */
private const val MATRIX_3X3_COLUMNS = 3

/** The number of columns of a 4x3 `simd` matrix. */
private const val MATRIX_4X3_COLUMNS = 4

/** The number of floats in a `simd_float3` column: 3 values, padded to 4 for 16-byte alignment. */
private const val SIMD_FLOAT3_STRIDE = 4

/** The size of a `matrix_float3x3` in bytes: 3 columns of a 16-byte `simd_float3`. */
private const val MATRIX_FLOAT3X3_SIZE = 48L

/**
 * Reads [columns] columns of [MATRIX_ROWS] floats from the given `simd` matrix memory, in column-major order (V5's
 * `toDoubleArray()`).
 */
private fun CPointer<FloatVar>.toDoubleArray(columns: Int): List<Double> = buildList(columns * MATRIX_ROWS) {
    for (column in 0 until columns) {
        for (row in 0 until MATRIX_ROWS) {
            add(this@toDoubleArray[column * SIMD_FLOAT3_STRIDE + row].toDouble())
        }
    }
}

/** Converts a 3x3 matrix to a `List<Double>`, in column-major order. */
internal fun CValue<simd_float3x3>.toDoubleArray(): List<Double> = useContents {
    columns.reinterpret<FloatVar>().toDoubleArray(MATRIX_3X3_COLUMNS)
}

/** Converts a 4x3 matrix to a `List<Double>`, in column-major order. */
internal fun CValue<simd_float4x3>.toDoubleArray(): List<Double> = useContents {
    columns.reinterpret<FloatVar>().toDoubleArray(MATRIX_4X3_COLUMNS)
}

/** Converts this [AVCameraCalibrationData] to a [CameraCalibrationData]. */
internal fun AVCameraCalibrationData.toCameraCalibrationData(): CameraCalibrationData = CameraCalibrationData(
    pixelSize = pixelSize.toDouble(),
    intrinsicMatrixReferenceDimensions = intrinsicMatrixReferenceDimensions.useContents {
        Size(width = width.toInt(), height = height.toInt())
    },
    lensDistortionCenter = lensDistortionCenter.useContents { Point(x = x, y = y) },
    cameraIntrinsicMatrix = intrinsicMatrix.toDoubleArray(),
    cameraExtrinsicsMatrix = extrinsicMatrix.toDoubleArray(),
)

/**
 * Reads the camera intrinsic matrix attached to this sample buffer
 * (`kCMSampleBufferAttachmentKey_CameraIntrinsicMatrix`, a `matrix_float3x3` wrapped in a `CFData`), in column-major
 * order, or `null` if it has none.
 */
internal fun CMSampleBufferRef.getCameraIntrinsicMatrix(): List<Double>? {
    val attachment = CMGetAttachment(this, kCMSampleBufferAttachmentKey_CameraIntrinsicMatrix, null)
        ?.takeIf { CFGetTypeID(it) == CFDataGetTypeID() }
        ?.reinterpret<__CFData>()
    val bytes = attachment
        ?.takeIf { CFDataGetLength(it) >= MATRIX_FLOAT3X3_SIZE }
        ?.let { CFDataGetBytePtr(it) }
    return bytes?.reinterpret<FloatVar>()?.toDoubleArray(MATRIX_3X3_COLUMNS)
}
