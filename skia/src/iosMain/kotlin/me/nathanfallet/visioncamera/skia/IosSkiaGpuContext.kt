@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.nathanfallet.visioncamera.skia

import cnames.structs.VCSkiaMetalContext
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import me.nathanfallet.visioncamera.NativeBuffer
import me.nathanfallet.visioncamera.skia.interop.vcskia_make_image_from_pixel_buffer
import me.nathanfallet.visioncamera.skia.interop.vcskia_metal_context_create
import me.nathanfallet.visioncamera.skia.interop.vcskia_metal_context_destroy
import me.nathanfallet.visioncamera.skia.interop.vcskia_metal_context_get_device
import me.nathanfallet.visioncamera.skia.interop.vcskia_metal_context_get_queue
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import platform.Foundation.NSRecursiveLock
import platform.posix.pthread_self

/**
 * A Metal device and command queue, and the Skia context drawing with them.
 *
 * Camera frames are `CVPixelBuffer`s, wrapped as Metal textures through a CVMetalTextureCache without copying them,
 * and sampled in place by Skia (see VisionCameraSkia.mm). skiko has no public API to wrap a native Skia image or to get
 * the native Skia context, so this file uses its internal `Image(ptr)` constructor and `DirectContext._ptr`.
 */
internal class IosSkiaGpuContext : SkiaGpuContext {
    private val metalContext: CPointer<VCSkiaMetalContext> = vcskia_metal_context_create()
        ?: error("Metal is not available on this device!")

    override val directContext: DirectContext = DirectContext.makeMetal(
        requireNotNull(vcskia_metal_context_get_device(metalContext)).rawValue,
        requireNotNull(vcskia_metal_context_get_queue(metalContext)).rawValue,
    )

    override fun makeCurrent() = Unit

    override fun makeFrameTexture(nativeBuffer: NativeBuffer): FrameTexture = memScoped {
        val errorMessage = allocArray<ByteVar>(ERROR_MESSAGE_LENGTH)
        errorMessage[0] = 0
        val image = vcskia_make_image_from_pixel_buffer(
            metalContext,
            requireNotNull(directContext._ptr.toLong().toCPointer<CPointed>()),
            requireNotNull(nativeBuffer.pointer.toCPointer<CPointed>()),
            errorMessage,
            ERROR_MESSAGE_LENGTH.toULong(),
        ) ?: error(errorMessage.toKString())
        FrameTexture(Image(image.rawValue))
    }

    override fun destroy(surfaces: List<Surface>, isOwnerThread: Boolean) {
        surfaces.forEach { it.close() }
        directContext.close()
        vcskia_metal_context_destroy(metalContext)
    }

    private companion object {
        const val ERROR_MESSAGE_LENGTH = 256
    }
}

internal actual fun createSkiaGpuContext(): SkiaGpuContext = IosSkiaGpuContext()

internal actual class SkiaLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

internal actual fun currentThreadId(): Long = pthread_self().toLong()
