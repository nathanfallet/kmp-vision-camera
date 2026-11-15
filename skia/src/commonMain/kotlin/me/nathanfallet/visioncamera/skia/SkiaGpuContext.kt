package me.nathanfallet.visioncamera.skia

import me.nathanfallet.visioncamera.NativeBuffer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceOrigin

/**
 * The GPU context of one rendering thread: an OpenGL ES context on Android, a Metal device on iOS.
 */
internal interface SkiaGpuContext {
    /** The Skia context drawing with this GPU context. */
    val directContext: DirectContext

    /** Makes this GPU context current on the calling thread, if the platform needs it (OpenGL ES). */
    fun makeCurrent()

    /**
     * Wraps the given native buffer (an `AHardwareBuffer*` or a `CVPixelBufferRef`) as a GPU texture without copying
     * it. The returned [FrameTexture] must be released before the native buffer.
     */
    fun makeFrameTexture(nativeBuffer: NativeBuffer): FrameTexture

    /**
     * Disposes the [surfaces] and this context, on its own thread if [isOwnerThread] is `true`, or on any other thread
     * (while the context's thread is not using it) otherwise.
     */
    fun destroy(surfaces: List<Surface>, isOwnerThread: Boolean)
}

/**
 * A native buffer wrapped as a Skia [image], with the platform resources to release with it.
 */
internal class FrameTexture(val image: Image, private val onRelease: () -> Unit = {}) {
    /** Releases the [image] and its platform resources. */
    fun release() {
        image.close()
        onRelease()
    }
}

/** Creates the GPU context of the current thread. */
internal expect fun createSkiaGpuContext(): SkiaGpuContext

/** The offscreen surfaces of every rendering thread, see [SurfacesCache]. */
internal val surfacesCache = SurfacesCache(
    backend = object : SurfacesCacheBackend<SkiaGpuContext, Surface> {
        override fun createContext(): SkiaGpuContext = createSkiaGpuContext()

        override fun createSurface(context: SkiaGpuContext, width: Int, height: Int): Surface {
            context.makeCurrent()
            return Surface.makeRenderTarget(
                context.directContext,
                false,
                ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
                0,
                SurfaceOrigin.TOP_LEFT,
                null,
            )
        }

        override fun disposeSurface(context: SkiaGpuContext, surface: Surface) {
            context.makeCurrent()
            surface.close()
        }

        override fun destroyContext(context: SkiaGpuContext, surfaces: List<Surface>, isOwnerThread: Boolean) =
            context.destroy(surfaces, isOwnerThread)
    },
    currentThreadId = ::currentThreadId,
    now = ::monotonicNowMs,
)

/**
 * Clears the internal offscreen surface cache, disposing every cached surface (and the GPU contexts of the rendering
 * threads).
 *
 * Surfaces are otherwise kept alive for up to 15 seconds of inactivity per thread to avoid re-creating them on every
 * Frame. Call this on teardown to release GPU memory eagerly. `SkiaCamera` calls it when it leaves the composition.
 */
fun clearSurfacesCache() = surfacesCache.clear()
