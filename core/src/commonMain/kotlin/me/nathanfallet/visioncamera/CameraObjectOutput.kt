package me.nathanfallet.visioncamera

/**
 * Configuration options for a [CameraObjectOutput].
 *
 * @see CameraFactory.createObjectOutput
 */
data class ObjectOutputOptions(
    /** The [ScannedObjectType]s to scan. */
    val enabledObjectTypes: List<ScannedObjectType>,
)

/**
 * The [CameraObjectOutput] scans objects (codes, faces, bodies…) with the platform's built-in detectors. iOS only:
 * on Android, use the `barcode` module (ML Kit) on a [CameraFrameOutput] instead.
 *
 * ```kotlin
 * val objectOutput = VisionCamera.createObjectOutput(ObjectOutputOptions(listOf(ScannedObjectType.QR)))
 * objectOutput.setOnObjectsScannedCallback { objects ->
 *     objects.filterIsInstance<ScannedCode>().forEach { println(it.value) }
 * }
 * ```
 *
 * @see CameraFactory.createObjectOutput
 */
interface CameraObjectOutput : CameraOutput {
    /** Sets the callback called with the objects scanned in each frame, or removes it with `null`. */
    fun setOnObjectsScannedCallback(onObjectsScanned: ((objects: List<ScannedObject>) -> Unit)?)
}
