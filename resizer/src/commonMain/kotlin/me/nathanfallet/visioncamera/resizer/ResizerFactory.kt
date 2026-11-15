package me.nathanfallet.visioncamera.resizer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.nathanfallet.visioncamera.VisionCameraException

/**
 * Create a new [Resizer] with the given [ResizerOptions] (the equivalent of VisionCamera's `createResizer`).
 *
 * The GPU pipeline is set up on a background thread. Check [isResizerAvailable] first to fall back to a CPU
 * implementation on devices without GPU support.
 *
 * ```kotlin
 * val resizer = createResizer(
 *     ResizerOptions(
 *         width = 192,
 *         height = 192,
 *         channelOrder = ChannelOrder.RGB,
 *         dataType = DataType.FLOAT32,
 *         scaleMode = ScaleMode.COVER,
 *         pixelLayout = PixelLayout.PLANAR,
 *     ),
 * )
 * ```
 *
 * @throws VisionCameraException If the GPU pipeline could not be created, e.g. because it is not available on this
 * device ([isResizerAvailable]).
 */
suspend fun createResizer(options: ResizerOptions): Resizer =
    // The pipeline is created even if the caller is cancelled meanwhile, so that the Resizer is returned (and the
    // caller can dispose it) instead of being lost between two threads.
    withContext(Dispatchers.Default + NonCancellable) {
        DefaultResizer(createPlatformResizerPipeline(options))
    }

/**
 * Returns whether the GPU-accelerated [Resizer] pipeline is available on this device.
 *
 * - On iOS, this requires the Metal GPU framework, which is always available on devices.
 * - On Android, this requires the Vulkan GPU framework and `AHardwareBuffer*` extensions, which are only available on
 *   Android 9 (API 28) or higher.
 *
 * ```kotlin
 * suspend fun getResizer(options: ResizerOptions): Resizer? =
 *     if (isResizerAvailable()) {
 *         // GPU accelerated Resizer pipeline is available!
 *         createResizer(options)
 *     } else {
 *         // GPU accelerated Resizer pipeline is not available on this device, fall back to a CPU implementation.
 *         null
 *     }
 * ```
 */
fun isResizerAvailable(): Boolean = isPlatformResizerAvailable()

/**
 * Returns whether the GPU pipeline of the current platform is available on this device.
 */
internal expect fun isPlatformResizerAvailable(): Boolean

/**
 * Creates the GPU pipeline of the current platform, for the given fixed [options].
 *
 * @throws VisionCameraException If the pipeline could not be created.
 */
internal expect fun createPlatformResizerPipeline(options: ResizerOptions): ResizerPipeline
