package me.nathanfallet.visioncamera

/**
 * Either a [Frame] or a [Depth] frame, as delivered together by a [CameraOutputSynchronizer]. This is VisionCamera's
 * `Frame | Depth` union.
 */
sealed interface FrameOrDepth
