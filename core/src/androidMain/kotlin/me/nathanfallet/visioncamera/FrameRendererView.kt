package me.nathanfallet.visioncamera

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * An Android [android.view.View] rendering the [Frame]s of a [FrameRenderer] on screen.
 *
 * ```kotlin
 * val renderer = VisionCamera.createFrameRenderer()
 * frameRendererView.renderer = renderer
 * frameOutput.setOnFrameCallback { frame ->
 *     renderer.renderFrame(frame)
 *     frame.dispose()
 * }
 * ```
 */
class FrameRendererView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs) {
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            (renderer as? NativeFrameRenderer)?.connectSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            (renderer as? NativeFrameRenderer)?.connectSurface(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            (renderer as? NativeFrameRenderer)?.disconnectSurface()
        }
    }

    /**
     * The [FrameRenderer] whose Frames are rendered into this view, created with
     * [CameraFactory.createFrameRenderer].
     */
    var renderer: FrameRenderer? = null
        set(value) {
            // Disconnect old Renderer
            (field as? NativeFrameRenderer)?.disconnectSurface()

            // Maybe connect new Renderer
            if (holder.surface.isValid) {
                (value as? NativeFrameRenderer)?.connectSurface(holder.surface)
            }
            field = value
        }

    init {
        holder.addCallback(surfaceCallback)
    }
}
