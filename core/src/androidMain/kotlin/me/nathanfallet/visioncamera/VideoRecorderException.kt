package me.nathanfallet.visioncamera

import androidx.camera.video.VideoRecordEvent

/**
 * A [VisionCameraException] from a CameraX recording that finalized with the given [errorCode].
 */
internal class VideoRecorderException(
    /** The `VideoRecordEvent.Finalize.ERROR_*` code. */
    val errorCode: Int,
    cause: Throwable? = null,
) : VisionCameraException(errorCode.toVideoRecordErrorName(), cause)

@Suppress("CyclomaticComplexMethod")
private fun Int.toVideoRecordErrorName(): String = when (this) {
    VideoRecordEvent.Finalize.ERROR_NONE -> "ERROR_NONE"
    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "ERROR_FILE_SIZE_LIMIT_REACHED"
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "ERROR_INSUFFICIENT_STORAGE"
    VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "ERROR_INVALID_OUTPUT_OPTIONS"
    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "ERROR_ENCODING_FAILED"
    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "ERROR_RECORDER_ERROR"
    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "ERROR_NO_VALID_DATA"
    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "ERROR_SOURCE_INACTIVE"
    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "ERROR_DURATION_LIMIT_REACHED"
    VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED -> "ERROR_RECORDING_GARBAGE_COLLECTED"
    else -> "ERROR_UNKNOWN ($this)"
}
