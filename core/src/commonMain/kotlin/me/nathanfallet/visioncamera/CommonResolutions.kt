package me.nathanfallet.visioncamera

/**
 * Commonly used resolutions, in portrait orientation, to be used as target resolutions for outputs.
 */
@Suppress("MagicNumber")
object CommonResolutions {
    /** 480x854 (16:9). */
    val VGA_16_9: Size = Size(480, 854)

    /** 480x640 (4:3). */
    val VGA_4_3: Size = Size(480, 640)

    /** 720x1280 (16:9). */
    val HD_16_9: Size = Size(720, 1280)

    /** 768x1024 (4:3). */
    val HD_4_3: Size = Size(768, 1024)

    /** 1080x1920 (16:9). */
    val FHD_16_9: Size = Size(1080, 1920)

    /** 1440x1920 (4:3). */
    val FHD_4_3: Size = Size(1440, 1920)

    /** 1440x2560 (16:9). */
    val QHD_16_9: Size = Size(1440, 2560)

    /** 1944x2592 (4:3). */
    val QHD_4_3: Size = Size(1944, 2592)

    /** 2160x3840 (16:9). */
    val UHD_16_9: Size = Size(2160, 3840)

    /** 3024x4032 (4:3). */
    val UHD_4_3: Size = Size(3024, 4032)

    /** 4536x8064 (16:9). */
    val `8K_16_9`: Size = Size(4536, 8064)

    /** 6048x8064 (4:3). */
    val `8K_4_3`: Size = Size(6048, 8064)

    /** The lowest possible 16:9 resolution. */
    val LOWEST_16_9: Size = Size(9, 16)

    /** The lowest possible 4:3 resolution. */
    val LOWEST_4_3: Size = Size(3, 4)

    /** The highest possible 16:9 resolution. */
    val HIGHEST_16_9: Size = Size(9000, 16000)

    /** The highest possible 4:3 resolution. */
    val HIGHEST_4_3: Size = Size(30000, 40000)
}
