package me.nathanfallet.visioncamera.resizer

/**
 * The mode tells the GPU resize pipeline how to map the input image into the output bounds when the aspect ratio
 * doesn't match.
 */
enum class ScaleMode {
    /**
     * Fill the whole output and keep the input aspect ratio. This performs a centered crop on the longer source axis.
     */
    COVER,

    /**
     * Keep the full source image visible and preserve aspect ratio. This adds black bars (letterboxing/pillarboxing)
     * where needed.
     */
    CONTAIN,

    /**
     * Fill the whole output by stretching the input independently on each axis. The aspect ratio is NOT preserved:
     * the input is squashed or stretched as needed to exactly match the output dimensions. No bars, no cropping.
     */
    STRETCH,
}
