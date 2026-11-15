package me.nathanfallet.visioncamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Converts [Frame]s and [Depth] frames to [Image]s (port of V5's `FrameConverter`). Use the [HybridFrameConverter]
 * instance:
 *
 * ```kotlin
 * frameOutput.setOnFrameCallback { frame ->
 *     val image = frame.use { HybridFrameConverter.convertFrameToImage(it) }
 *     // ... display or process the image
 * }
 * ```
 *
 * The resulting [Image] is a copy: it stays valid after the [Frame] or [Depth] is disposed.
 */
interface FrameConverter {
    /**
     * Converts the given [frame] to an [Image], with its orientation and mirroring applied. This performs a CPU copy,
     * synchronously on the calling thread.
     *
     * @throws VisionCameraException If the [frame] is invalid ([Frame.isValid]) or was not created by VisionCamera.
     */
    fun convertFrameToImage(frame: Frame): Image

    /**
     * Same as [convertFrameToImage], but runs on a background dispatcher. The [frame] must not be disposed before this
     * returns.
     *
     * @throws VisionCameraException If the [frame] is invalid ([Frame.isValid]) or was not created by VisionCamera.
     */
    suspend fun convertFrameToImageAsync(frame: Frame): Image =
        withContext(Dispatchers.Default) { convertFrameToImage(frame) }

    /**
     * Converts the given [depth] frame to an [Image], with its orientation and mirroring applied. The resulting image
     * is a grey-scale RGB image, where black pixels are far away and white pixels are close by.
     *
     * @throws VisionCameraException If the [depth] is invalid ([Depth.isValid]) or was not created by VisionCamera.
     */
    fun convertDepthToImage(depth: Depth): Image

    /**
     * Same as [convertDepthToImage], but runs on a background dispatcher. The [depth] must not be disposed before this
     * returns.
     *
     * @throws VisionCameraException If the [depth] is invalid ([Depth.isValid]) or was not created by VisionCamera.
     */
    suspend fun convertDepthToImageAsync(depth: Depth): Image =
        withContext(Dispatchers.Default) { convertDepthToImage(depth) }
}

/**
 * The platform's [FrameConverter] (V5's `HybridFrameConverter`).
 */
object HybridFrameConverter : FrameConverter by createPlatformFrameConverter()

/**
 * Creates the [FrameConverter] of the current platform.
 */
internal expect fun createPlatformFrameConverter(): FrameConverter
