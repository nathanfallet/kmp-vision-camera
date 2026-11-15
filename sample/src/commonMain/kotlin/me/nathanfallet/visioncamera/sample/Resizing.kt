package me.nathanfallet.visioncamera.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.nathanfallet.visioncamera.CameraFrameOutput
import me.nathanfallet.visioncamera.FrameOutputOptions
import me.nathanfallet.visioncamera.TargetVideoPixelFormat
import me.nathanfallet.visioncamera.compose.rememberFrameOutput
import me.nathanfallet.visioncamera.resizer.ChannelOrder
import me.nathanfallet.visioncamera.resizer.DataType
import me.nathanfallet.visioncamera.resizer.GPUFrame
import me.nathanfallet.visioncamera.resizer.PixelLayout
import me.nathanfallet.visioncamera.resizer.ResizerOptions
import me.nathanfallet.visioncamera.resizer.ScaleMode
import me.nathanfallet.visioncamera.resizer.compose.rememberResizer
import me.nathanfallet.visioncamera.resizer.isResizerAvailable
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** The input of a typical ML model: 192x192, RGB, float32, CHW (planar), like VisionCamera's resizer docs. */
private val modelInput = ResizerOptions(
    width = 192,
    height = 192,
    channelOrder = ChannelOrder.RGB,
    dataType = DataType.FLOAT32,
    scaleMode = ScaleMode.COVER,
    pixelLayout = PixelLayout.PLANAR,
)

/**
 * A YUV frame output resizing every frame on the GPU with a [rememberResizer] Resizer, like an ML model's input,
 * and measuring the FPS, the resize time and the center pixel's color.
 */
@Composable
internal fun rememberResizerStats(): Pair<CameraFrameOutput, String?> {
    var stats by remember { mutableStateOf<String?>(null) }
    val counter = remember { FrameCounter() }
    val resizerState = rememberResizer(modelInput)
    val resizer by rememberUpdatedState(resizerState.resizer)
    val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
        frame.use {
            val current = resizer ?: return@use
            val start = TimeSource.Monotonic.markNow()
            val resized = try {
                current.resize(frame)
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                // CameraX swallows the errors of frame processors: show it.
                println("VisionCameraSample resize failed: $error")
                stats = "Resize failed: ${error.message}"
                return@use
            }
            resized.use {
                val milliseconds = start.elapsedNow().inWholeMicroseconds / MICROSECONDS_PER_MILLISECOND
                counter.onFrame {
                    "${frame.width}x${frame.height} → ${resized.width}x${resized.height} ${resized.dataType} " +
                        "${resized.pixelLayout} in ${milliseconds.roundToInt()} ms, center ${resized.centerPixel()}"
                }?.let { stats = it }
            }
        }
    }
    val status = when {
        resizerState.error != null -> "Resizer error: ${resizerState.error?.message}"
        !isResizerAvailable() -> "Resizer: not available on this device"
        else -> stats ?: "Resizer: loading"
    }
    return frameOutput to status
}

/** The RGB values (0...255) of the center pixel of this float32 planar [GPUFrame]. */
private fun GPUFrame.centerPixel(): String {
    val bytes = getPixelBuffer()
    val plane = width * height
    val center = (height / 2) * width + width / 2
    return (0 until CHANNELS).joinToString(",", "rgb(", ")") { channel ->
        val offset = (channel * plane + center) * Float.SIZE_BYTES
        val bits = (0 until Float.SIZE_BYTES).fold(0) { acc, i -> acc or ((bytes[offset + i].toInt() and 0xFF) shl (8 * i)) }
        (Float.fromBits(bits) * MAX_CHANNEL_VALUE).roundToInt().toString()
    }
}

private const val CHANNELS = 3
private const val MAX_CHANNEL_VALUE = 255
private const val MICROSECONDS_PER_MILLISECOND = 1_000.0
