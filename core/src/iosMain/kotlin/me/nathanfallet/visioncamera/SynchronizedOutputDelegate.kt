package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDataOutputSynchronizer
import platform.AVFoundation.AVCaptureDataOutputSynchronizerDelegateProtocol
import platform.AVFoundation.AVCaptureDepthDataOutput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureOutputDataDroppedReason
import platform.AVFoundation.AVCaptureOutputDataDroppedReasonNone
import platform.AVFoundation.AVCaptureSynchronizedData
import platform.AVFoundation.AVCaptureSynchronizedDataCollection
import platform.AVFoundation.AVCaptureSynchronizedDepthData
import platform.AVFoundation.AVCaptureSynchronizedSampleBufferData
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVMediaTypeDepthData
import platform.AVFoundation.AVMediaTypeVideo
import platform.darwin.NSObject
import kotlin.concurrent.AtomicReference

/**
 * The [AVCaptureDataOutputSynchronizerDelegateProtocol] of a [CameraOutputSynchronizer] (port of V5's
 * `SynchronizedOutputDelegate.swift`). Every synchronized sample is wrapped in an [IosFrame] or an [IosDepth].
 *
 * The callbacks can be set from any thread; they are called on the synchronizer's queue.
 */
internal class SynchronizedOutputDelegate : NSObject(), AVCaptureDataOutputSynchronizerDelegateProtocol {
    private val onFramesRef = AtomicReference<((List<FrameOrDepth>) -> Unit)?>(null)
    private val onFrameDroppedRef =
        AtomicReference<((MediaType, AVCaptureOutputDataDroppedReason) -> Unit)?>(null)

    /** Called with each set of synchronized frames. */
    var onFrames: ((List<FrameOrDepth>) -> Unit)?
        get() = onFramesRef.value
        set(value) {
            onFramesRef.value = value
        }

    /** Called with the [MediaType] and reason of each dropped frame. */
    var onFrameDropped: ((MediaType, AVCaptureOutputDataDroppedReason) -> Unit)?
        get() = onFrameDroppedRef.value
        set(value) {
            onFrameDroppedRef.value = value
        }

    override fun dataOutputSynchronizer(
        synchronizer: AVCaptureDataOutputSynchronizer,
        didOutputSynchronizedDataCollection: AVCaptureSynchronizedDataCollection,
    ) {
        val onFrames = onFrames ?: return

        val datas = mutableListOf<FrameOrDepth>()
        for (output in synchronizer.dataOutputs.filterIsInstance<AVCaptureOutput>()) {
            val data = didOutputSynchronizedDataCollection.synchronizedDataForCaptureOutput(output)
            if (data == null) {
                Logger.info("AVCaptureSynchronizedDataCollection does not contain data for $output!")
                continue
            }
            try {
                getSynchronizedFrame(synchronizer, data)?.let { datas.add(it) }
            } catch (e: VisionCameraException) {
                Logger.error("Failed to capture AVCaptureSynchronizedData $data! Error: ${e.message}")
                onFrameDropped?.invoke(MediaType.OTHER, AVCaptureOutputDataDroppedReasonNone)
            }
        }
        onFrames(datas)
    }

    /** Wraps the given synchronized [data] in a [Frame] or a [Depth], or returns `null` if it was dropped. */
    private fun getSynchronizedFrame(
        synchronizer: AVCaptureDataOutputSynchronizer,
        data: AVCaptureSynchronizedData,
    ): FrameOrDepth? = when (data) {
        // Video Frame
        is AVCaptureSynchronizedSampleBufferData -> if (data.sampleBufferWasDropped) {
            onFrameDropped?.invoke(MediaType.VIDEO, data.droppedReason)
            null
        } else {
            getSynchronizedVideoFrame(synchronizer, data)
        }
        // Depth Frame
        is AVCaptureSynchronizedDepthData -> if (data.depthDataWasDropped) {
            onFrameDropped?.invoke(MediaType.DEPTH, data.droppedReason)
            null
        } else {
            getSynchronizedDepthFrame(synchronizer, data)
        }
        // ????? Frame
        else -> {
            Logger.error("SynchronizedOutputDelegate received an unknown data type! $data")
            null
        }
    }

    private inline fun <reified T : AVCaptureOutput> getConnectedOutput(
        synchronizer: AVCaptureDataOutputSynchronizer,
    ): T = synchronizer.dataOutputs.filterIsInstance<T>().firstOrNull()
        ?: throw VisionCameraException(
            "AVCaptureDataOutputSynchronizer does not contain an output of type ${T::class.simpleName}!",
        )

    private fun getConnection(output: AVCaptureOutput, mediaType: String?): AVCaptureConnection =
        output.connectionWithMediaType(mediaType)
            ?: throw VisionCameraException("$output does not have a $mediaType connection!")

    private fun getSynchronizedVideoFrame(
        synchronizer: AVCaptureDataOutputSynchronizer,
        data: AVCaptureSynchronizedSampleBufferData,
    ): Frame {
        val videoOutput = getConnectedOutput<AVCaptureVideoDataOutput>(synchronizer)
        val connection = getConnection(videoOutput, AVMediaTypeVideo)
        val buffer = data.sampleBuffer ?: throw VisionCameraException("$data does not contain a sample buffer!")
        val metadata = MediaSampleMetadata.fromConnection(timestamp = data.timestamp, connection = connection)
        return IosFrame(buffer = buffer, metadata = metadata)
    }

    private fun getSynchronizedDepthFrame(
        synchronizer: AVCaptureDataOutputSynchronizer,
        data: AVCaptureSynchronizedDepthData,
    ): Depth {
        val depthOutput = getConnectedOutput<AVCaptureDepthDataOutput>(synchronizer)
        val connection = getConnection(depthOutput, AVMediaTypeDepthData)
        val metadata = MediaSampleMetadata.fromConnection(timestamp = data.timestamp, connection = connection)
        return IosDepth(depthData = data.depthData, metadata = metadata)
    }
}
