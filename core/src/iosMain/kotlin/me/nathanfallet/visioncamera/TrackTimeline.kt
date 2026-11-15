package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.CoreMedia.CMClockGetTime
import platform.CoreMedia.CMClockRef
import platform.CoreMedia.CMTime

/** The type of a [Track] (port of V5's `TrackType`). */
internal enum class TrackType {
    AUDIO,
    VIDEO,
}

/** An event of a [TrackTimeline] (port of V5's `TimelineEvent.swift`). */
internal class TimelineEvent(val type: Type, val timestamp: CValue<CMTime>) {
    enum class Type {
        START,
        PAUSE,
        RESUME,
        STOP,
    }

    override fun toString(): String = "${timestamp.seconds}: $type"
}

/**
 * Represents the timeline of a specific track in an asset (video, audio). The timeline can be started and stopped,
 * and can contain pauses in between (port of V5's `TrackTimeline.swift`).
 *
 * The [TrackTimeline] assumes that all timestamps passed to [isTimestampWithinTimeline] are ordered incrementally,
 * and once a timestamp arrives after a timeline has been stopped, it will mark the track as finished (see
 * [isFinished]).
 */
internal class TrackTimeline(private val trackType: TrackType, private val clock: CMClockRef) {
    private val events = mutableListOf<TimelineEvent>()

    /**
     * Represents whether the timeline has been marked as finished or not. A timeline will automatically be marked as
     * finished when a timestamp arrives that appears after a [stop].
     */
    var isFinished = false
        private set

    /** The latency of the buffers in this timeline, computed by (currentTime - mostRecentBuffer.timestamp). */
    var latency: CValue<CMTime> = zeroCMTime
        private set

    /** The first actually written timestamp of this timeline. */
    var firstTimestamp: CValue<CMTime>? = null
        private set

    /** The last actually written timestamp of this timeline. */
    var lastTimestamp: CValue<CMTime>? = null
        private set

    /**
     * Whether the [TrackTimeline] is currently paused, or not. A [TrackTimeline] is _not_ paused when it's running,
     * stopped, or not-yet-started.
     */
    val isPaused: Boolean get() = events.lastOrNull()?.type == TimelineEvent.Type.PAUSE

    val targetDuration: CValue<CMTime>
        get() {
            val first = events.firstOrNull() ?: return zeroCMTime
            val last = events.last()
            return last.timestamp - first.timestamp - totalPauseDuration
        }

    val actualDuration: CValue<CMTime>
        get() {
            val first = firstTimestamp
            val last = lastTimestamp
            return if (first == null || last == null) zeroCMTime else last - first - totalPauseDuration
        }

    val pauses: List<CValue<CMTime>>
        get() {
            val result = mutableListOf<CValue<CMTime>>()
            var currentPauseStart: CValue<CMTime>? = null
            for (event in events) {
                when (event.type) {
                    // if this is null, we "start" counting a pause
                    TimelineEvent.Type.PAUSE -> if (currentPauseStart == null) currentPauseStart = event.timestamp
                    TimelineEvent.Type.RESUME -> {
                        // if a pause has been started counting, we finish counting the pause here
                        currentPauseStart?.let { result.add(event.timestamp - it) }
                        currentPauseStart = null
                    }
                    // start/stop doesn't matter
                    else -> Unit
                }
            }
            // if a pause is still "open", it has not yet been resumed.
            currentPauseStart?.let { result.add(CMClockGetTime(clock) - it) }
            return result
        }

    val totalPauseDuration: CValue<CMTime>
        get() = pauses.fold(zeroCMTime) { total, pause -> total + pause }

    fun start() = addEvent(TimelineEvent.Type.START, "Requesting $trackType timeline start")

    fun pause() = addEvent(TimelineEvent.Type.PAUSE, "Pausing $trackType timeline")

    fun resume() = addEvent(TimelineEvent.Type.RESUME, "Resuming $trackType timeline")

    fun stop() = addEvent(TimelineEvent.Type.STOP, "Requesting $trackType timeline stop")

    private fun addEvent(type: TimelineEvent.Type, message: String) {
        val now = CMClockGetTime(clock)
        events.add(TimelineEvent(type, now))
        Logger.info("$message at ${now.seconds}...")
    }

    fun isTimestampWithinTimeline(timestamp: CValue<CMTime>): Boolean {
        val now = CMClockGetTime(clock)
        latency = now - timestamp

        val result = isTimestampWithinTimelineImpl(timestamp)
        if (result) {
            if (firstTimestamp == null) {
                Logger.info("$trackType timeline: First timestamp: ${timestamp.seconds}")
                firstTimestamp = timestamp
            }
            lastTimestamp = timestamp
        }
        return result
    }

    @Suppress("ReturnCount")
    private fun isTimestampWithinTimelineImpl(timestamp: CValue<CMTime>): Boolean {
        // The track is already finished. It cannot be in the timeline anymore.
        if (isFinished) return false

        // Iterate through timeline to make sure the timestamp is within our total range (start - stop), and outside
        // of any pauses.
        var isPaused = false
        for (event in events) {
            when (event.type) {
                // If it's before a session has been started we want to encode it in the video. It will not appear if
                // it is actually before the session start time, but we still encode it to prevent blank frame
                // flashes.
                TimelineEvent.Type.START -> if (timestamp < event.timestamp) return true
                TimelineEvent.Type.PAUSE -> isPaused = true
                TimelineEvent.Type.RESUME -> {
                    // It's within a pause.
                    if (isPaused && timestamp < event.timestamp) return false
                    isPaused = false
                }
                TimelineEvent.Type.STOP -> if (timestamp > event.timestamp) {
                    // It's after the track was stopped. Mark this track as finished now.
                    markAsFinished(lastTimestamp = timestamp, stopTimestamp = event.timestamp)
                    return false
                }
            }
        }

        // If no resume was called, it's still paused! Otherwise, it passed all of our checks - it's within the
        // timeline!
        return !isPaused
    }

    private fun markAsFinished(lastTimestamp: CValue<CMTime>, stopTimestamp: CValue<CMTime>) {
        isFinished = true
        val diff = lastTimestamp - stopTimestamp
        Logger.info(
            "Last timestamp arrived at ${lastTimestamp.seconds} (${diff.seconds} seconds after stop()) - $trackType " +
                "Timeline is now finished!",
        )
        Logger.info("Timeline:\n${events.joinToString("\n")}")
    }
}
