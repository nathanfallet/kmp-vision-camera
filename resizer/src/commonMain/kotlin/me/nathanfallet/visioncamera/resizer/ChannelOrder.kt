package me.nathanfallet.visioncamera.resizer

/**
 * Represents how the three output channels are ordered in memory.
 *
 * For example, [RGB] stores one pixel as `R, G, B`.
 */
enum class ChannelOrder {
    /** Stores one pixel as `R, G, B`. */
    RGB,

    /** Stores one pixel as `B, G, R`. */
    BGR,
}
