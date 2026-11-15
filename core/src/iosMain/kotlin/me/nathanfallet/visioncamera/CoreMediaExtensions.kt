package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreMedia.CMSampleBufferCreateCopyWithNewTiming
import platform.CoreMedia.CMSampleBufferGetSampleTimingInfoArray
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMSampleBufferRefVar
import platform.CoreMedia.CMSampleTimingInfo
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeAdd
import platform.CoreMedia.CMTimeCompare
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeSubtract
import platform.CoreMedia.kCMTimeZero

// Port of V5's `CMSampleBuffer+copyWithTimestampOffset.swift` and `CMTime+inverted.swift`, plus the `CMTime`
// operators Swift gets for free.

/** `noErr`. */
private const val NO_ERROR = 0

/** A zero [CMTime] (`CMTime.zero`). */
internal val zeroCMTime: CValue<CMTime> get() = kCMTimeZero.readValue()

/** The seconds of this [CMTime] (`CMTime.seconds`). */
internal val CValue<CMTime>.seconds: Double get() = CMTimeGetSeconds(this)

internal operator fun CValue<CMTime>.plus(other: CValue<CMTime>): CValue<CMTime> = CMTimeAdd(this, other)

internal operator fun CValue<CMTime>.minus(other: CValue<CMTime>): CValue<CMTime> = CMTimeSubtract(this, other)

internal operator fun CValue<CMTime>.compareTo(other: CValue<CMTime>): Int = CMTimeCompare(this, other)

/** Inverts the time, e.g. 3.5 seconds -> -3.5 seconds. */
internal fun CValue<CMTime>.inverted(): CValue<CMTime> = useContents { CMTimeMake(-value, timescale) }

/**
 * Returns a copy of this sample buffer with the timing info adjusted by the given [offset]: the decode and
 * presentation timestamps are adjusted by the given offset (+).
 *
 * The returned buffer is retained (+1): the caller must `CFRelease` it.
 */
internal fun CMSampleBufferRef.copyWithTimestampOffset(offset: CValue<CMTime>): CMSampleBufferRef = memScoped {
    val count = alloc<LongVar>()
    val getCountStatus = CMSampleBufferGetSampleTimingInfoArray(this@copyWithTimestampOffset, 0, null, count.ptr)
    if (getCountStatus != NO_ERROR) {
        throw VisionCameraException("Failed to get the sample buffer's timing info! (status: $getCountStatus)")
    }
    val entries = count.value
    if (entries <= 0L) throw VisionCameraException("No timing entries found in the sample buffer!")

    val infos = allocArray<CMSampleTimingInfo>(entries)
    val getArrayStatus = CMSampleBufferGetSampleTimingInfoArray(this@copyWithTimestampOffset, entries, infos, null)
    if (getArrayStatus != NO_ERROR) {
        throw VisionCameraException("Failed to get the sample buffer's timing info! (status: $getArrayStatus)")
    }
    for (index in 0 until entries) {
        val info = infos[index]
        (info.presentationTimeStamp.readValue() + offset).place(info.presentationTimeStamp.ptr)
        (info.decodeTimeStamp.readValue() + offset).place(info.decodeTimeStamp.ptr)
    }

    val newBuffer = alloc<CMSampleBufferRefVar>()
    val copyResult = CMSampleBufferCreateCopyWithNewTiming(
        null,
        this@copyWithTimestampOffset,
        entries,
        infos,
        newBuffer.ptr,
    )
    if (copyResult != NO_ERROR) {
        throw VisionCameraException("Failed to copy the sample buffer! (status: $copyResult)")
    }
    newBuffer.value ?: throw VisionCameraException("The copied sample buffer is null!")
}
