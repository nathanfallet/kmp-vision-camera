package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.Image

/**
 * Represents a Barcode Scanner that uses ML Kit Barcodes.
 *
 * The [BarcodeScanner] can be used in a Frame Processor by calling [scanCodes], or for static images by calling
 * [scanCodesInImageAsync]. It holds native resources: [dispose] it (or use it in a `use { }` block) once it is no
 * longer needed.
 *
 * ```kotlin
 * val scanner = createBarcodeScanner(BarcodeScannerOptions(listOf(TargetBarcodeFormat.ALL_FORMATS)))
 * frameOutput.setOnFrameCallback { frame ->
 *     frame.use { scanner.scanCodes(it) }.forEach { println("Barcode value: ${it.rawValue}") }
 * }
 * ```
 *
 * @see createBarcodeScanner
 */
interface BarcodeScanner : AutoCloseable {
    /**
     * Synchronously detects [Barcode]s in the given [frame].
     *
     * All coordinates in the [Barcode] are relative to the [Frame]'s coordinate system.
     *
     * You can convert [Barcode] coordinates to Camera coordinates using [Frame.convertFramePointToCameraPoint], and
     * then convert the Camera coordinates to Preview View coordinates using the preview view's
     * `convertCameraPointToViewPoint`.
     *
     * ```kotlin
     * val barcodes = scanner.scanCodes(frame)
     * for (barcode in barcodes) {
     *     println("Barcode value: ${barcode.rawValue}")
     *     for (point in barcode.cornerPoints) {
     *         val cameraPoint = frame.convertFramePointToCameraPoint(point)
     *         val previewPoint = previewView.convertCameraPointToViewPoint(cameraPoint)
     *         println("Corner Point: $previewPoint")
     *     }
     * }
     * ```
     */
    fun scanCodes(frame: Frame): List<Barcode>

    /**
     * Asynchronously detects [Barcode]s in the given [frame].
     *
     * The [Frame] must remain valid until this function returns. Do not call [Frame.dispose] before it returns.
     *
     * @see scanCodes
     */
    suspend fun scanCodesAsync(frame: Frame): List<Barcode>

    /**
     * Asynchronously detects [Barcode]s in the given [image].
     *
     * This can be used to scan codes in an existing still image, such as a captured photo
     * ([me.nathanfallet.visioncamera.Photo.toImage]) or an image wrapped from a platform image (`Bitmap.toImage()`
     * on Android, `UIImage.toImage()` on iOS).
     *
     * All coordinates in the returned [Barcode]s are relative to the given [Image]'s coordinate system.
     *
     * ```kotlin
     * createBarcodeScanner(BarcodeScannerOptions(listOf(TargetBarcodeFormat.ALL_FORMATS))).use { scanner ->
     *     val barcodes = scanner.scanCodesInImageAsync(image)
     *     println("Barcode value: ${barcodes.firstOrNull()?.rawValue}")
     * }
     * ```
     */
    suspend fun scanCodesInImageAsync(image: Image): List<Barcode>

    /**
     * Disposes this [BarcodeScanner], releasing its native resources. It must not be used afterwards.
     */
    fun dispose()

    /**
     * Same as [dispose], to use a [BarcodeScanner] with `use { }`.
     */
    override fun close() {
        dispose()
    }
}
