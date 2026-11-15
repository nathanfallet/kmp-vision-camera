package me.nathanfallet.visioncamera.skia.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.nathanfallet.visioncamera.skia.Transform
import kotlin.concurrent.Volatile

/**
 * The state of a [SkiaCamera]: the rendered Frame it displays, its size, and the transform of the last Frame.
 */
internal class SkiaCameraState {
    /** The displayed Frame. Only read and written on the main thread. */
    var previewFrame: SkiaPreviewFrame? by mutableStateOf(null)
        private set

    /** The size of the canvas, in pixels. */
    @Volatile
    var canvasSize: IntSize = IntSize.Zero

    /** The orientation and mirroring of the last rendered Frame. */
    @Volatile
    var lastFrameTransform: Transform? = null

    private var isDisposed = false

    /** Displays the given [frame] (from any thread), closing the previous one. */
    fun post(frame: SkiaPreviewFrame) {
        mainScope.launch {
            if (isDisposed) {
                frame.close()
                return@launch
            }
            val oldFrame = previewFrame
            previewFrame = frame
            oldFrame?.close()
        }
    }

    /** Closes the displayed Frame, and the ones still posted. Must be called on the main thread. */
    fun dispose() {
        isDisposed = true
        previewFrame?.close()
        previewFrame = null
    }

    private companion object {
        /** Frames are swapped on the main thread, where they are drawn, so that a displayed Frame is never closed. */
        val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
