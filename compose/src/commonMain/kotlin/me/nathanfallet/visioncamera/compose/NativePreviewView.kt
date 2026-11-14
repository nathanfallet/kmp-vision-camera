package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.PreviewImplementationMode
import me.nathanfallet.visioncamera.PreviewResizeMode

/**
 * Displays the Camera feed of a [CameraPreviewOutput], using the platform's native preview view
 * (`me.nathanfallet.visioncamera.PreviewView`, an Android `View` or an iOS `UIView`).
 *
 * @param previewOutput The [CameraPreviewOutput] to display.
 * @param modifier The [Modifier] of this view.
 * @param resizeMode How the Camera feed is scaled into the view's bounds.
 * @param implementationMode Whether the preview uses a `SurfaceView` or a `TextureView` (Android only).
 * @param onPreviewStarted Called when the preview started streaming (Android only for now).
 * @param onPreviewStopped Called when the preview stopped streaming (Android only for now).
 */
@Suppress("LongParameterList")
@Composable
expect fun NativePreviewView(
    previewOutput: CameraPreviewOutput?,
    modifier: Modifier = Modifier,
    resizeMode: PreviewResizeMode = PreviewResizeMode.COVER,
    implementationMode: PreviewImplementationMode = PreviewImplementationMode.PERFORMANCE,
    onPreviewStarted: (() -> Unit)? = null,
    onPreviewStopped: (() -> Unit)? = null,
)
