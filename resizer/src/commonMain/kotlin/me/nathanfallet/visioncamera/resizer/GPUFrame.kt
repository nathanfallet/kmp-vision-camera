package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.VisionCameraException

/**
 * A [GPUFrame] represents a buffer on the GPU, often the result of a [Frame] that has been resized with the
 * [Resizer].
 *
 * The [GPUFrame] exposes its underlying pixel buffer to the CPU via [getPixelBuffer] (a copy), or without copying via
 * the platform accessors: `getPixelByteBuffer()` (a direct `java.nio.ByteBuffer`) on Android, `getPixelData()` (an
 * `NSData`) on iOS.
 *
 * The [Resizer] reuses one output buffer, so only one [GPUFrame] of a [Resizer] can be alive at a time: it must be
 * [dispose]d (or used in a `use { }` block) before the next [Resizer.resize] call.
 *
 * ```kotlin
 * resizer.resize(frame).use { resized ->
 *     val pixels = resized.getPixelBuffer()
 *     // run your ML model...
 * }
 * ```
 */
interface GPUFrame : AutoCloseable {
    /**
     * The width of this [GPUFrame], in pixels.
     *
     * If the [GPUFrame] has already been disposed, this returns `0`.
     */
    val width: Int

    /**
     * The height of this [GPUFrame], in pixels.
     *
     * If the [GPUFrame] has already been disposed, this returns `0`.
     */
    val height: Int

    /**
     * The channel ordering of this [GPUFrame].
     *
     * If the [GPUFrame] has already been disposed, this returns `null`.
     */
    val channelOrder: ChannelOrder?

    /**
     * The scalar data type of this [GPUFrame].
     *
     * If the [GPUFrame] has already been disposed, this returns `null`.
     */
    val dataType: DataType?

    /**
     * How this [GPUFrame]'s channels are laid out in memory:
     * - [PixelLayout.INTERLEAVED] corresponds to HWC / NHWC.
     * - [PixelLayout.PLANAR] corresponds to CHW / NCHW.
     *
     * If the [GPUFrame] has already been disposed, this returns `null`.
     */
    val pixelLayout: PixelLayout?

    /**
     * Gets the pixels of this [GPUFrame]: `width * height * 3` values of [dataType] (1 byte for [DataType.INT8] and
     * [DataType.UINT8], 2 for [DataType.FLOAT16], 4 for [DataType.FLOAT32], in the platform's native byte order,
     * little-endian on every supported device), arranged as [pixelLayout] describes.
     *
     * Unlike VisionCamera's `ArrayBuffer`, the returned [ByteArray] is a copy of the GPU buffer, so it stays valid
     * after this [GPUFrame] is disposed. To read the GPU buffer without copying (valid until this [GPUFrame] is
     * disposed), use `getPixelByteBuffer()` on Android or `getPixelData()` on iOS.
     *
     * @throws VisionCameraException If this [GPUFrame] has already been disposed.
     */
    fun getPixelBuffer(): ByteArray

    /**
     * Disposes this [GPUFrame], giving its buffer back to the [Resizer] so it can resize the next [Frame]. Calling it
     * more than once is a no-op.
     */
    fun dispose()

    /**
     * Same as [dispose], to use a [GPUFrame] with `use { }`.
     */
    override fun close() {
        dispose()
    }
}
