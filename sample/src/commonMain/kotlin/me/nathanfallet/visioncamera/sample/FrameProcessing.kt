package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import me.nathanfallet.visioncamera.CameraFrameOutput
import me.nathanfallet.visioncamera.CameraObjectOutput
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.FrameOutputOptions
import me.nathanfallet.visioncamera.ScannedCode
import me.nathanfallet.visioncamera.ScannedObjectType
import me.nathanfallet.visioncamera.TargetVideoPixelFormat
import me.nathanfallet.visioncamera.compose.PreviewViewRef
import me.nathanfallet.visioncamera.compose.rememberFrameOutput
import me.nathanfallet.visioncamera.compose.rememberObjectOutput
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Whether the platform has a [CameraObjectOutput] (iOS only, like VisionCamera). */
internal expect val supportsObjectOutput: Boolean

/**
 * A YUV frame output measuring how many frames per second reach the frame processor, with their size and format.
 */
@Composable
internal fun rememberFrameStats(): Pair<CameraFrameOutput, String?> {
    var stats by remember { mutableStateOf<String?>(null) }
    val counter = remember { FrameCounter() }
    val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
        frame.use { counter.onFrame(it)?.let { text -> stats = text } }
    }
    return frameOutput to stats
}

/** Counts frames on the frame output's thread, and returns a summary once per second. */
private class FrameCounter {
    private var windowStart = TimeSource.Monotonic.markNow()
    private var count = 0

    fun onFrame(frame: Frame): String? {
        count++
        val elapsed = windowStart.elapsedNow()
        if (elapsed.inWholeMilliseconds < WINDOW_MS) return null
        val fps = count * MS_PER_SECOND / elapsed.inWholeMilliseconds.toDouble()
        windowStart = TimeSource.Monotonic.markNow()
        count = 0
        return "${fps.roundToInt()} FPS, ${frame.width}x${frame.height} ${frame.pixelFormat}"
    }

    private companion object {
        const val WINDOW_MS = 1000L
        const val MS_PER_SECOND = 1000.0
    }
}

/** An object output scanning QR codes and EAN-13 barcodes on iOS, or `null` on Android. */
@Composable
internal fun rememberCodeScanner(): Pair<CameraObjectOutput?, List<ScannedCode>> {
    if (!supportsObjectOutput) return null to emptyList()
    var codes by remember { mutableStateOf(emptyList<ScannedCode>()) }
    val objectOutput = rememberObjectOutput(listOf(ScannedObjectType.QR, ScannedObjectType.EAN_13)) { objects ->
        codes = objects.filterIsInstance<ScannedCode>()
    }
    return objectOutput to codes
}

/** Outlines the scanned [codes] on top of the preview, converting their corners to view coordinates. */
@Composable
internal fun ScannedCodesOverlay(codes: List<ScannedCode>, previewRef: PreviewViewRef, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (!previewRef.isReady) return@Canvas
        codes.forEach { code ->
            val points = code.cornerPoints.map { previewRef.convertCameraPointToViewPoint(it) }
            if (points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Color.Green, style = Stroke(width = 4.dp.toPx()))
        }
    }
}
