package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.Exif
import java.io.File
import java.nio.ByteBuffer

/** The file extension (including the dot) of this [PhotoContainerFormat]. */
internal val PhotoContainerFormat.fileExtension: String
    get() = when (this) {
        PhotoContainerFormat.DCM -> ".dcm"
        PhotoContainerFormat.DNG -> ".dng"
        PhotoContainerFormat.HEIC -> ".heic"
        PhotoContainerFormat.JPEG -> ".jpg"
        PhotoContainerFormat.TIFF -> ".tiff"
        PhotoContainerFormat.UNKNOWN -> ""
    }

/** The file extension (including the dot) of this [TargetPhotoContainerFormat]. */
internal val TargetPhotoContainerFormat.fileExtension: String
    get() = when (this) {
        TargetPhotoContainerFormat.DNG -> PhotoContainerFormat.DNG
        TargetPhotoContainerFormat.HEIC -> PhotoContainerFormat.HEIC
        TargetPhotoContainerFormat.JPEG, TargetPhotoContainerFormat.NATIVE -> PhotoContainerFormat.JPEG
    }.fileExtension

/** Converts this [TargetPhotoContainerFormat] to a CameraX `ImageCapture` output format. */
internal fun TargetPhotoContainerFormat.toCameraXOutputFormat(allowHDR: Boolean): Int = when (this) {
    // JPEG with Ultra HDR, or normal JPEG
    TargetPhotoContainerFormat.JPEG ->
        if (allowHDR) ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR else ImageCapture.OUTPUT_FORMAT_JPEG
    TargetPhotoContainerFormat.HEIC ->
        throw VisionCameraException("PhotoContainerFormat \"heic\" is not supported on this Device!")
    // DNG = RAW
    TargetPhotoContainerFormat.DNG -> ImageCapture.OUTPUT_FORMAT_RAW
    // JPEG is the default format on Android
    TargetPhotoContainerFormat.NATIVE -> ImageCapture.OUTPUT_FORMAT_JPEG
}

/** Converts an Android [ImageFormat] to a [PhotoContainerFormat]. */
internal fun photoContainerFormatFromImageFormat(imageFormat: Int): PhotoContainerFormat = when {
    imageFormat == ImageFormat.HEIC || imageFormat == ImageFormat.HEIC_ULTRAHDR -> PhotoContainerFormat.HEIC
    ImageFormatUtils.isRawFormat(imageFormat) -> PhotoContainerFormat.DNG
    ImageFormatUtils.isPhotoFormat(imageFormat) -> PhotoContainerFormat.JPEG
    else -> PhotoContainerFormat.UNKNOWN
}

/** The [PhotoContainerFormat] of this [ImageProxy]. */
internal val ImageProxy.photoContainerFormat: PhotoContainerFormat
    get() = when (format) {
        ImageFormat.JPEG, ImageFormat.JPEG_R, ImageFormat.DEPTH_JPEG -> PhotoContainerFormat.JPEG
        ImageFormat.RAW10, ImageFormat.RAW12, ImageFormat.RAW_SENSOR, ImageFormat.RAW_PRIVATE ->
            PhotoContainerFormat.DNG
        else -> PhotoContainerFormat.UNKNOWN
    }

/** Whether this [ImageProxy] is a RAW image. */
internal val ImageProxy.isRAW: Boolean get() = ImageFormatUtils.isRawFormat(format)

/** The [CameraOrientation] of this [ImageProxy]. */
internal val ImageProxy.orientation: CameraOrientation
    get() = cameraOrientationFromDegrees(imageInfo.rotationDegrees)

/** Copies this buffer's readable contents (from its start, without moving its position) into a new [ByteArray]. */
internal fun ByteBuffer.toReadableByteArray(): ByteArray {
    val view = duplicate()
    view.rewind()
    val bytes = ByteArray(view.remaining())
    view.get(bytes)
    return bytes
}

/** Creates a [PhotoFile] describing the photo captured to [file]. */
@SuppressLint("RestrictedApi")
internal fun photoFileFromFile(file: File, imageFormat: Int, timestamp: Double, isMirrored: Boolean): PhotoFile {
    val exif = Exif.createFromFile(file)
    val containerFormat = photoContainerFormatFromImageFormat(imageFormat)
    return PhotoFile(
        filePath = file.absolutePath,
        width = exif.width,
        height = exif.height,
        orientation = cameraOrientationFromDegrees(exif.rotation),
        isMirrored = isMirrored,
        timestamp = timestamp,
        isRawPhoto = containerFormat == PhotoContainerFormat.DNG,
        containerFormat = containerFormat,
    )
}
