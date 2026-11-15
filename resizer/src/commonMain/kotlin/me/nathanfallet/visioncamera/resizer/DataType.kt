package me.nathanfallet.visioncamera.resizer

/**
 * Represents the scalar encoding used for each output channel.
 *
 * For example, [FLOAT32] stores each output value as a 32-bit float in the normalized range from `0.0` to `1.0`.
 */
enum class DataType {
    /** A signed 8-bit integer per channel, from `-128` to `127` (the `0..255` value shifted by `-128`). */
    INT8,

    /** An unsigned 8-bit integer per channel, from `0` to `255`. */
    UINT8,

    /** A 16-bit (half precision) float per channel, in the normalized range from `0.0` to `1.0`. */
    FLOAT16,

    /** A 32-bit float per channel, in the normalized range from `0.0` to `1.0`. */
    FLOAT32,
}
