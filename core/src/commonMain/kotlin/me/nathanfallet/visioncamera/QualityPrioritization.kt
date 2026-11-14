package me.nathanfallet.visioncamera

/**
 * Specifies the prioritization between speed and quality for photo capture.
 */
enum class QualityPrioritization {
    /** Captures photos as fast as possible, possibly with zero-shutter-lag (ZSL). */
    SPEED,

    /** Usually the default, balances capture latency with quality. */
    BALANCED,

    /** Ensures photos are well exposed and allows enough time for maximum quality. */
    QUALITY,
}
