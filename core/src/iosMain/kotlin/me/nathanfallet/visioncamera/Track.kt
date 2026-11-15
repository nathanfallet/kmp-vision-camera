package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.AVFoundation.AVAssetWriterInput
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMClockRef
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTime

/**
 * A video or audio track of an `AVAssetWriter`, writing the sample buffers that fall within its [TrackTimeline]
 * (port of V5's `Track.swift`).
 */
internal class Track(
    private val type: TrackType,
    private val assetWriterInput: AVAssetWriterInput,
    clock: CMClockRef,
) {
    private val timeline = TrackTimeline(type, clock)

    /**
     * Whether the track has been marked as finished or not. A track is finished when [stop] was called, and a buffer
     * with a later timestamp than the one from the [stop] call arrives.
     */
    val isFinished: Boolean get() = timeline.isFinished

    /** Whether the track is currently paused or not. */
    val isPaused: Boolean get() = timeline.isPaused

    /** When a buffer arrives, it might arrive with a small delay - [latency] represents this delay. */
    val latency: CValue<CMTime> get() = timeline.latency

    /** The current total duration of the timeline. */
    val duration: CValue<CMTime> get() = timeline.actualDuration

    /** The last timestamp that was actually written to the track. */
    var lastTimestamp: CValue<CMTime>? = null
        private set

    fun start() = timeline.start()

    fun stop() = timeline.stop()

    fun pause() = timeline.pause()

    fun resume() = timeline.resume()

    fun append(originalBuffer: CMSampleBufferRef) {
        // 1. If the track is already finished (from a previous call), don't write anything.
        if (timeline.isFinished) return

        // 2. Track is not yet finished - add the timestamp to the timeline
        val originalTimestamp = CMSampleBufferGetPresentationTimeStamp(originalBuffer)
        val shouldWrite = timeline.isTimestampWithinTimeline(originalTimestamp)

        if (shouldWrite) {
            // 3. If there is a pause, we need to offset the buffers by the pause duration, otherwise the video is
            // actually frozen for the pause duration. Encoders ain't smart.
            val pauseOffset = timeline.totalPauseDuration
            val copy = pauseOffset.takeIf { it.seconds > 0 }?.let {
                originalBuffer.copyWithTimestampOffset(it.inverted())
            }
            try {
                write(copy ?: originalBuffer)
            } finally {
                // The copy is retained (+1), release it once written.
                copy?.let { CFRelease(it) }
            }
        }

        // 5. Check again; if the track is NOW finished, we want to finalize it.
        if (timeline.isFinished) {
            val diff = (timeline.actualDuration - timeline.targetDuration).seconds
            val difference =
                if (diff > 0) "$diff seconds longer than expected" else "${-diff} seconds shorter than expected"
            Logger.info(
                "Marking $type track as finished - target duration: ${timeline.targetDuration.seconds} seconds, " +
                    "actual duration: ${timeline.actualDuration.seconds} seconds ($difference)",
            )
            assetWriterInput.markAsFinished()
        }
    }

    private fun write(buffer: CMSampleBufferRef) {
        val timestamp = CMSampleBufferGetPresentationTimeStamp(buffer)
        // 4. Write the buffer
        if (assetWriterInput.readyForMoreMediaData) {
            // Asset Writer is ready - write the buffer!
            if (assetWriterInput.appendSampleBuffer(buffer)) {
                lastTimestamp = timestamp
            } else {
                // Something went wrong when writing the buffer
                Logger.error("Failed to write $type buffer at ${timestamp.seconds} to track!")
            }
        } else {
            // Asset Writer is not ready! We have to drop this frame.
            Logger.error(
                "Failed to write $type buffer at ${timestamp.seconds} to track - the Asset Writer was not yet ready " +
                    "for more data!",
            )
        }
    }
}
