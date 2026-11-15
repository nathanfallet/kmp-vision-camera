package me.nathanfallet.visioncamera

import me.nathanfallet.visioncamera.interop.VCSynchronizedMediaTypeDepth
import me.nathanfallet.visioncamera.interop.VCSynchronizedMediaTypeVideo
import me.nathanfallet.visioncamera.interop.VCSynchronizedOutputDelegate
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutputDataDroppedReason
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMake
import kotlin.concurrent.AtomicReference

/**
 * The delegate of a [CameraOutputSynchronizer] (port of V5's `SynchronizedOutputDelegate.swift`). Every synchronized
 * sample is wrapped in an [IosFrame] or an [IosDepth].
 *
 * The synchronized data collections are read by an Objective-C delegate ([native], see VisionCameraNative.h): as
 * Kotlin references, they would stay retained (with their video and depth buffers) until the next garbage
 * collection, and AVFoundation's buffer pools would run out.
 *
 * The callbacks can be set from any thread; they are called on the synchronizer's queue.
 */
internal class SynchronizedOutputDelegate {
    private val onFramesRef = AtomicReference<((List<FrameOrDepth>) -> Unit)?>(null)
    private val onFrameDroppedRef =
        AtomicReference<((MediaType, AVCaptureOutputDataDroppedReason) -> Unit)?>(null)

    /** The frames of the collection being delivered (the delegate is called on a serial queue). */
    private val pendingFrames = AtomicReference<MutableList<FrameOrDepth>?>(null)

    /** The [VCSynchronizedOutputDelegate] to set on the `AVCaptureDataOutputSynchronizer`. */
    val native: VCSynchronizedOutputDelegate = createNativeDelegate(onFramesRef, onFrameDroppedRef, pendingFrames)

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

    private companion object {
        // The blocks only capture these references, not the SynchronizedOutputDelegate: a Kotlin object referenced by
        // a block retained by Objective-C is not collected while the block is alive.
        fun createNativeDelegate(
            onFramesRef: AtomicReference<((List<FrameOrDepth>) -> Unit)?>,
            onFrameDroppedRef: AtomicReference<((MediaType, AVCaptureOutputDataDroppedReason) -> Unit)?>,
            pendingFrames: AtomicReference<MutableList<FrameOrDepth>?>,
        ): VCSynchronizedOutputDelegate = VCSynchronizedOutputDelegate().apply {
            onBegin = { pendingFrames.value = mutableListOf() }
            onVideo = { sampleBuffer, timestampValue, timestampScale, connection ->
                // Video Frame (the Frame retains the buffer)
                val buffer: CMSampleBufferRef = checkNotNull(sampleBuffer)
                val metadata = metadataOf(timestampValue, timestampScale, checkNotNull(connection))
                pendingFrames.value?.add(IosFrame(buffer = buffer, metadata = metadata))
            }
            onDepth = { depthData, timestampValue, timestampScale, connection ->
                // Depth Frame (`depthData` is a +1 reference, owned by the Depth from now on)
                val metadata = metadataOf(timestampValue, timestampScale, checkNotNull(connection))
                pendingFrames.value?.add(IosDepth(depthData = checkNotNull(depthData), metadata = metadata))
            }
            onDropped = { mediaType, reason ->
                val type = when (mediaType) {
                    VCSynchronizedMediaTypeVideo -> MediaType.VIDEO
                    VCSynchronizedMediaTypeDepth -> MediaType.DEPTH
                    else -> MediaType.OTHER
                }
                onFrameDroppedRef.value?.invoke(type, reason)
            }
            onEnd = {
                val frames = pendingFrames.getAndSet(null).orEmpty()
                val onFrames = onFramesRef.value
                if (onFrames != null) onFrames(frames) else frames.forEach(::dispose)
            }
        }

        /** Disposes a frame that nobody will receive. */
        fun dispose(frame: FrameOrDepth) = when (frame) {
            is Frame -> frame.dispose()
            is Depth -> frame.dispose()
        }

        fun metadataOf(timestampValue: Long, timestampScale: Int, connection: AVCaptureConnection) =
            MediaSampleMetadata.fromConnection(
                timestamp = CMTimeMake(timestampValue, timestampScale),
                connection = connection,
            )
    }
}
