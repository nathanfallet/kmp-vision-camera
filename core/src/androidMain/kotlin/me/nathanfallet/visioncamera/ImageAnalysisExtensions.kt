package me.nathanfallet.visioncamera

import androidx.camera.core.ImageAnalysis

/**
 * Sets the [ImageAnalysis] backpressure strategy: drops late frames if [allowDroppingLateFrames], otherwise blocks
 * the producer.
 */
internal fun ImageAnalysis.Builder.setAllowDroppingLateFrames(allowDroppingLateFrames: Boolean): ImageAnalysis.Builder =
    if (allowDroppingLateFrames) {
        // Limitation: This does not the same as `alwaysDiscardsLateVideoFrames` on iOS - this actually overwrites the
        //  latest buffer...
        setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    } else {
        setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
    }
