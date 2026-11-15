package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.nathanfallet.visioncamera.FrameRenderer
import me.nathanfallet.visioncamera.FrameRendererView

/** Displays the Frames of a [FrameRenderer] in an Android [FrameRendererView], see the common declaration. */
@Composable
actual fun NativeFrameRendererView(
    renderer: FrameRenderer?,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context -> FrameRendererView(context) },
        modifier = modifier,
        update = { view -> if (view.renderer !== renderer) view.renderer = renderer },
        onRelease = { view -> view.renderer = null },
    )
}
