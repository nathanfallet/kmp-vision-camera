package me.nathanfallet.visioncamera.resizer

/**
 * Represents how the individual channels per pixel are arranged in memory.
 *
 * - [INTERLEAVED] stores complete pixels next to each other, which corresponds to (N)HWC:
 *     ```text
 *     RGBRGBRGBRGB
 *     RGBRGBRGBRGB
 *     RGBRGBRGBRGB
 *     ```
 * - [PLANAR] stores one full channel plane after the other, which corresponds to (N)CHW:
 *     ```text
 *     RRRRRRRRRRRR
 *     GGGGGGGGGGGG
 *     BBBBBBBBBBBB
 *     ```
 */
enum class PixelLayout {
    /** Complete pixels next to each other: HWC / NHWC, e.g. an ML model input tensor shaped `[1, H, W, 3]`. */
    INTERLEAVED,

    /** One full channel plane after the other: CHW / NCHW, e.g. an ML model input tensor shaped `[1, 3, H, W]`. */
    PLANAR,
}
