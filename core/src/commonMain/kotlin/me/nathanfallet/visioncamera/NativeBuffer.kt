package me.nathanfallet.visioncamera

/**
 * An unmanaged memory pointer to a native platform buffer.
 *
 * Consumers (GPU renderers, ML runtimes…) can use the [pointer] to unwrap the native buffer, and must [release]
 * it as soon as they are done with it.
 *
 * ```kotlin
 * val nativeBuffer = frame.getNativeBuffer()
 * // your processing...
 * nativeBuffer.release()
 * frame.dispose()
 * ```
 */
interface NativeBuffer {
    /**
     * A `uintptr_t` to the native platform buffer, with a retain count of +1.
     * - On iOS, this points to a `CVPixelBufferRef`.
     * - On Android, this points to an `AHardwareBuffer*`.
     */
    val pointer: Long

    /**
     * Release this reference to the platform buffer again, effectively decrementing the retain count by 1.
     * This must always be called as soon as possible, otherwise the pipeline will stall.
     */
    fun release()
}
