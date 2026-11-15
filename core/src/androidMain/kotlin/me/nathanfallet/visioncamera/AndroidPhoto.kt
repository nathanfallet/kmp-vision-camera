package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.Exif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android [Photo], wrapping an in-memory CameraX [ImageProxy].
 */
internal class AndroidPhoto(
    /** The captured CameraX image. */
    val image: ImageProxy,
    override val isMirrored: Boolean,
    private val location: android.location.Location?,
) : Photo {
    private val isDisposed = AtomicBoolean(false)

    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val orientation: CameraOrientation get() = image.orientation
    override val timestamp: Double get() = image.imageInfo.timestamp / NANOSECONDS_PER_SECOND
    override val isRawPhoto: Boolean get() = image.isRAW
    override val containerFormat: PhotoContainerFormat get() = image.photoContainerFormat

    // Only CPU-accessible planes can be copied into a ByteArray.
    override val hasPixelBuffer: Boolean get() = image.planes.isNotEmpty()

    // V5 does not support depth and calibration data for Photos on Android yet.
    override val depth: Depth? = null

    override val calibrationData: CameraCalibrationData? = null

    override fun getPixelBuffer(): ByteArray {
        val planes = image.planes
        if (planes.isEmpty()) throw VisionCameraException("Photo does not contain any readable Pixel Data!")
        // A single plane is copied as is, several planes are concatenated.
        val buffers = planes.map { it.buffer.toReadableByteArray() }
        return if (buffers.size == 1) buffers.single() else buffers.reduce { acc, bytes -> acc + bytes }
    }

    override suspend fun saveToFile(path: String) = withContext(Dispatchers.IO) {
        saveToFile(File(path))
    }

    override suspend fun saveToTemporaryFile(): String = withContext(Dispatchers.IO) {
        val file = File.createTempFile("VisionCamera_", containerFormat.fileExtension)
        saveToFile(file)
        file.absolutePath
    }

    override suspend fun getFileData(): ByteArray = withContext(Dispatchers.IO) {
        // JPEG Images have a single plane of image data.
        if (image.format != ImageFormat.JPEG) {
            throw VisionCameraException(
                "Cannot get File Data for Photos with Image Format \"${image.format}\" until " +
                    "https://issuetracker.google.com/issues/482079661 is implemented!",
            )
        }
        image.planes.single().buffer.toReadableByteArray()
    }

    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) image.close()
    }

    /** Converts this photo to a [Bitmap], applying its [orientation] and mirroring. */
    fun toBitmap(): Bitmap {
        // This throws on RAW (DNG) Photos, because CameraX's toBitmap() only supports JPEG, RGB and YUV.
        val bitmap = image.toBitmap()
        val matrix = Matrix().apply {
            if (orientation != CameraOrientation.UP) postRotate(orientation.counterRotated().degrees.toFloat())
            if (isMirrored) postScale(-1f, 1f)
        }
        // No transforms needed
        if (matrix.isIdentity) return bitmap
        val transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        bitmap.recycle()
        return transformedBitmap
    }

    private fun saveToFile(file: File) {
        // JPEG Images have a single plane of image data.
        if (image.format != ImageFormat.JPEG) {
            throw VisionCameraException(
                "Photos with ImageFormat \"${image.format}\" cannot be saved to a File until " +
                    "https://issuetracker.google.com/issues/482079661 is implemented!",
            )
        }
        val bytes = image.planes.single().buffer.toReadableByteArray()
        FileOutputStream(file).use { it.write(bytes) }
        attachExifData(file)
    }

    /** Writes mirroring, location and timestamp in the EXIF metadata of the given JPEG [file]. */
    @SuppressLint("RestrictedApi")
    private fun attachExifData(file: File) {
        val exif = Exif.createFromFile(file)
        if (isMirrored) exif.flipHorizontally()
        // JPEG buffers already carry imageInfo.rotationDegrees in EXIF. Exif.rotate(...) composes with the existing
        // orientation tag, so rotating here would apply it twice.
        location?.let { exif.attachLocation(it) }
        exif.attachTimestamp()
        exif.save()
    }
}

/**
 * Converts this [Photo] to an Android [Bitmap], with its [Photo.orientation] and mirroring applied.
 *
 * It replaces V5's `toImage()`. RAW photos can not be converted.
 *
 * @throws VisionCameraException If this [Photo] was not created by VisionCamera.
 */
fun Photo.toBitmap(): Bitmap {
    val photo = this as? AndroidPhoto ?: throw VisionCameraException("This Photo was not created by VisionCamera!")
    return photo.toBitmap()
}
