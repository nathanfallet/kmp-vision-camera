package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.PreviewImplementationMode
import me.nathanfallet.visioncamera.PreviewResizeMode
import me.nathanfallet.visioncamera.PreviewView

@Suppress("LongParameterList")
@Composable
actual fun NativePreviewView(
    previewOutput: CameraPreviewOutput?,
    modifier: Modifier,
    resizeMode: PreviewResizeMode,
    implementationMode: PreviewImplementationMode,
    onPreviewStarted: (() -> Unit)?,
    onPreviewStopped: (() -> Unit)?,
) {
    AndroidView(
        factory = { context -> PreviewView(context) },
        modifier = modifier,
        update = { view ->
            if (view.previewOutput !== previewOutput) view.previewOutput = previewOutput
            view.resizeMode = resizeMode
            view.implementationMode = implementationMode
            view.onPreviewStarted = onPreviewStarted
            view.onPreviewStopped = onPreviewStopped
        },
        onRelease = { view -> view.previewOutput = null },
    )
}
