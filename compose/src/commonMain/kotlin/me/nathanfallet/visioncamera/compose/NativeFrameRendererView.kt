package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.nathanfallet.visioncamera.FrameRenderer

/**
 * Displays the [me.nathanfallet.visioncamera.Frame]s rendered by a [FrameRenderer], using the platform's native
 * view (`me.nathanfallet.visioncamera.FrameRendererView`, an Android `SurfaceView` or an iOS `UIView`). The
 * equivalent of VisionCamera's `<NativeFrameRendererView />`.
 *
 * ```kotlin
 * val renderer = rememberFrameRenderer()
 * val frameOutput = rememberFrameOutput { frame ->
 *     frame.use { renderer.renderFrame(it) }
 * }
 * NativeFrameRendererView(renderer, Modifier.fillMaxSize())
 * ```
 *
 * @param renderer The [FrameRenderer] whose Frames are displayed, see [rememberFrameRenderer], or `null` to display
 * nothing.
 * @param modifier The [Modifier] of this view.
 */
@Composable
expect fun NativeFrameRendererView(
    renderer: FrameRenderer?,
    modifier: Modifier = Modifier,
)
