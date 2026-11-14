package me.nathanfallet.visioncamera

/**
 * Represents the reason why a [Frame] was dropped by the Camera pipeline.
 */
enum class FrameDroppedReason {
    /** The Frame took too long to process and could not be delivered in time. */
    FRAME_WAS_LATE,

    /** The Camera pipeline ran out of buffers, most likely because previous Frames were not disposed fast enough. */
    OUT_OF_BUFFERS,

    /** A discontinuity in the Frame stream occurred, typically when the session was interrupted or reconfigured. */
    DISCONTINUITY,

    /** The Frame was dropped for an unknown reason. */
    UNKNOWN,
}
