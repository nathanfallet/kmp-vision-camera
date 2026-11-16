package me.nathanfallet.visioncamera.resizer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.awaitCancellation
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.resizer.Resizer
import me.nathanfallet.visioncamera.resizer.ResizerOptions
import me.nathanfallet.visioncamera.resizer.createResizer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Use a [Resizer] with the given [options] (the equivalent of VisionCamera's `useResizer`).
 *
 * The [Resizer] can be used to resize and convert [Frame]s for ML processing. It is created asynchronously (the
 * returned [ResizerState] is [ResizerState.Loading] meanwhile), kept across recompositions, recreated when the
 * [options] change, and disposed once it leaves the composition (where V5 lets the garbage collector release it).
 *
 * Inspect the returned [ResizerState.error] if the [Resizer] couldn't be created successfully.
 *
 * As the [Resizer] is disposed on the UI thread while [Frame]s keep arriving on the Frame output's thread, a [Frame]
 * delivered right as the [options] change or the composable leaves may call [Resizer.resize] on the disposed
 * [Resizer], which then throws a `VisionCameraException`: catch it if your Frame callback must not throw.
 *
 * ```kotlin
 * val resizer = rememberResizer(
 *     ResizerOptions(
 *         width = 192,
 *         height = 192,
 *         channelOrder = ChannelOrder.RGB,
 *         dataType = DataType.FLOAT32,
 *         scaleMode = ScaleMode.COVER,
 *         pixelLayout = PixelLayout.PLANAR,
 *     ),
 * ).resizer
 * val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
 *     frame.use {
 *         resizer?.resize(it)?.use { resized ->
 *             val pixels = resized.getPixelBuffer()
 *             // run your ML model...
 *         }
 *     }
 * }
 * ```
 *
 * @param options The output size and format of the [Resizer].
 */
@Composable
fun rememberResizer(options: ResizerOptions): ResizerState {
    val state = remember(options) { mutableStateOf<ResizerState>(ResizerState.Loading) }
    LaunchedEffect(state) {
        val resizer = try {
            createResizer(options)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            state.value = ResizerState.Error(error)
            return@LaunchedEffect
        }
        try {
            state.value = ResizerState.Ready(resizer)
            awaitCancellation()
        } finally {
            // If a Frame is still being resized, the Resizer is released once its GPUFrame is disposed.
            resizer.dispose()
        }
    }
    return state.value
}
