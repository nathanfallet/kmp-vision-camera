package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.Frame

/**
 * Configures options for a [Resizer].
 *
 * ```kotlin
 * val options = ResizerOptions(
 *     width = 192,
 *     height = 192,
 *     channelOrder = ChannelOrder.RGB,
 *     dataType = DataType.FLOAT32,
 *     scaleMode = ScaleMode.COVER,
 *     pixelLayout = PixelLayout.PLANAR,
 * )
 * ```
 *
 * @property width Configures the width the [Resizer] will resize [Frame]s to, in pixels. Must be greater than zero.
 * @property height Configures the height the [Resizer] will resize [Frame]s to, in pixels. Must be greater than zero.
 * @property channelOrder Configures the output [ChannelOrder] the [Resizer] will write for each resized pixel.
 * @property dataType Configures the scalar [DataType] the [Resizer] will use for each output channel.
 * @property scaleMode Configures the resize behavior when input and output aspect ratios differ:
 * - [ScaleMode.COVER]: Perform a centered crop to fill the full output.
 * - [ScaleMode.CONTAIN]: Keep the full source image and pad remaining output areas with black bars.
 * - [ScaleMode.STRETCH]: Stretch the input independently on each axis to exactly fill the output. Does NOT preserve
 *   aspect ratio.
 * @property pixelLayout Configures how the individual channels per pixel are arranged in memory:
 * - Use [PixelLayout.INTERLEAVED] if your ML model input tensor is (N)HWC shaped, e.g. `[1, H, W, 3]`.
 * - Use [PixelLayout.PLANAR] if your ML model input tensor is (N)CHW shaped, e.g. `[1, 3, H, W]`.
 *
 * @throws IllegalArgumentException If [width] or [height] is not greater than zero, or if one output buffer would be
 * larger than 2 GB.
 */
data class ResizerOptions(
    val width: Int,
    val height: Int,
    val channelOrder: ChannelOrder,
    val dataType: DataType,
    val scaleMode: ScaleMode,
    val pixelLayout: PixelLayout,
) {
    init {
        require(width > 0 && height > 0) {
            "Resizer output dimensions must be greater than zero, got ${width}x$height."
        }
        require(outputByteCountOf(channelOrder, dataType, width, height) <= Int.MAX_VALUE) {
            "Resizer output buffer of ${width}x$height is too large."
        }
    }
}
