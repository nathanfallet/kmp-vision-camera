package me.nathanfallet.visioncamera.skia

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import me.nathanfallet.visioncamera.NativeBuffer
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.GLBackendState
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceOrigin
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An offscreen OpenGL ES context (with a 1x1 pbuffer surface) and the Skia context drawing with it.
 *
 * Camera frames are `AHardwareBuffer`s, imported as external textures through an EGLImage without copying them, like
 * Skia's own GrAHardwareBufferUtils (skiko has no `SkImages::DeferredFromAHardwareBuffer`).
 */
internal class AndroidSkiaGpuContext : SkiaGpuContext {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also { display ->
        check(display != EGL14.EGL_NO_DISPLAY) { "Failed to get the EGL display!" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Failed to initialize EGL!" }
    }
    private val eglContext: EGLContext
    private val pbuffer: EGLSurface

    init {
        val (config, clientVersion) = chooseConfig(GLES_3_CLIENT_VERSION) ?: chooseConfig(GLES_2_CLIENT_VERSION)
            ?: error("No OpenGL ES config available for Skia!")
        eglContext = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Failed to create an OpenGL ES context: ${EGL14.eglGetError()}" }
        pbuffer = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0,
        )
        check(pbuffer != EGL14.EGL_NO_SURFACE) { "Failed to create a pbuffer surface: ${EGL14.eglGetError()}" }
    }

    override val directContext: DirectContext = run {
        makeCurrent()
        DirectContext.makeGL()
    }

    override fun makeCurrent() {
        if (EGL14.eglGetCurrentContext() == eglContext) return
        check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext)) {
            "Failed to make the OpenGL ES context current: ${EGL14.eglGetError()}"
        }
    }

    override fun makeFrameTexture(nativeBuffer: NativeBuffer): FrameTexture {
        makeCurrent()
        val result = LongArray(IMPORT_RESULT_SIZE)
        HardwareBufferTexture.nativeImport(nativeBuffer.pointer, result)
        val eglImage = result[0]
        val texture = result[1].toInt()
        val width = result[2].toInt()
        val height = result[HEIGHT_INDEX].toInt()
        val destroyEglImage = { HardwareBufferTexture.nativeDestroyImage(display.nativeHandle, eglImage) }
        // The JNI code bound the texture behind Skia's back.
        directContext.resetGL(GLBackendState.TEXTURE_BINDING)
        val backendTexture = BackendTexture.makeGL(
            width = width,
            height = height,
            isMipmapped = false,
            textureId = texture,
            textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textureFormat = GLES30.GL_RGBA8,
        )
        val image = try {
            // Skia owns (and deletes) the texture from now on.
            Image.adoptTextureFrom(directContext, backendTexture, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            destroyEglImage()
            throw e
        } finally {
            backendTexture.close()
        }
        return FrameTexture(image, destroyEglImage)
    }

    override fun destroy(surfaces: List<Surface>, isOwnerThread: Boolean) {
        if (isOwnerThread) {
            makeCurrent()
        } else {
            // The context is current on its own thread: Skia must not issue any OpenGL ES call from this one. Its GPU
            // resources are freed with the EGL context, once its thread releases it.
            directContext.abandon()
        }
        surfaces.forEach { it.close() }
        directContext.close()
        if (isOwnerThread) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        EGL14.eglDestroySurface(display, pbuffer)
        EGL14.eglDestroyContext(display, eglContext)
    }

    private fun chooseConfig(clientVersion: Int): Pair<EGLConfig, Int>? {
        val renderableType = if (clientVersion == GLES_3_CLIENT_VERSION) {
            EGLExt.EGL_OPENGL_ES3_BIT_KHR
        } else {
            EGL14.EGL_OPENGL_ES2_BIT
        }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, COLOR_CHANNEL_BITS,
            EGL14.EGL_GREEN_SIZE, COLOR_CHANNEL_BITS,
            EGL14.EGL_BLUE_SIZE, COLOR_CHANNEL_BITS,
            EGL14.EGL_ALPHA_SIZE, COLOR_CHANNEL_BITS,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val isChosen = EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
        val config = configs[0]
        return if (isChosen && count[0] > 0 && config != null) config to clientVersion else null
    }

    private companion object {
        const val GLES_3_CLIENT_VERSION = 3
        const val GLES_2_CLIENT_VERSION = 2
        const val COLOR_CHANNEL_BITS = 8
        const val IMPORT_RESULT_SIZE = 4
        const val HEIGHT_INDEX = 3
    }
}

internal actual fun createSkiaGpuContext(): SkiaGpuContext = AndroidSkiaGpuContext()

internal actual class SkiaLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

internal actual fun currentThreadId(): Long = android.os.Process.myTid().toLong()
