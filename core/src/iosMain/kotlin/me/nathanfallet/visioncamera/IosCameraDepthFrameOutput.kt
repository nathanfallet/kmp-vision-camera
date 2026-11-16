package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDepthDataOutput
import me.nathanfallet.visioncamera.interop.VCDepthDataOutputDelegate
import platform.AVFoundation.AVMediaTypeDepthData
import platform.CoreMedia.CMTimeMake
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile

/**
 * The iOS [CameraDepthFrameOutput], streaming the depth data of an [AVCaptureDepthDataOutput] as [Depth] frames (port
 * of V5's `HybridCameraDepthFrameOutput.swift`).
 *
 * Unlike V5, where the callbacks must be set on the output's [thread] (a worklet constraint), they can be set from any
 * thread; they always run on [thread].
 */
internal class IosCameraDepthFrameOutput(
    private val options: DepthFrameOutputOptions,
) : CameraDepthFrameOutput, NativeCameraOutput {
    // An Objective-C delegate (VisionCameraNative.h), so that AVDepthData never becomes a Kotlin reference retained
    // until the next garbage collection, which would exhaust AVFoundation's depth buffers.
    private val delegate = VCDepthDataOutputDelegate()
    private val queue = createSerialQueue(QUEUE_LABEL)
    private val isDisposed = AtomicInt(0)

    // Like V5, `configure(...)` sets it on the session queue while Depth frames read it on the Depth queue, so an old
    // Depth frame might read a newer `mirrorMode`.
    @Volatile
    private var mirrorMode: MirrorMode = MirrorMode.AUTO

    override val mediaType: MediaType = MediaType.DEPTH
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = true
    override val output: AVCaptureDepthDataOutput = AVCaptureDepthDataOutput()

    override val thread: NativeThread by lazy { IosNativeThread(queue = queue, label = QUEUE_LABEL) }

    private val depthConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeDepthData)

    @Volatile
    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            val connection = depthConnection ?: return
            if (options.enablePhysicalBufferRotation) {
                runCatching { connection.setOrientation(value) }
            }
        }

    override val currentResolution: Size?
        get() = depthConnection?.inputStreamResolution

    override val streamType: StreamType = StreamType.DEPTH_VIDEO

    override val targetResolution: ResolutionRule
        get() = ResolutionRule.ClosestTo(options.targetResolution)

    init {
        // Set up our `delegate`
        output.setDelegate(delegate, callbackQueue = queue)
        // If the pipeline stalls, drop Depth frames to avoid blowing up RAM
        output.alwaysDiscardsLateDepthData = options.dropFramesWhileBusy
        output.filteringEnabled = options.enableFiltering
        if (isIOSAtLeast(major = 26) && output.deferredStartSupported) {
            // Deferred start allows the Session to delay this output's startup in favor of preview-related outputs to
            // make preview appear faster.
            output.deferredStartEnabled = options.allowDeferredStart
        }
    }

    override fun configure(config: OutputConfiguration) {
        // Like V5, this is not attached to the `connection`, so there may be race conditions where we read
        // `mirrorMode` for an old Depth frame.
        mirrorMode = config.mirrorMode

        val connection = depthConnection ?: return
        if (options.enablePhysicalBufferRotation) {
            // Pipeline should physically rotate buffers on the `connection`
            runCatching { connection.setOrientation(outputOrientation) }
            runCatching { connection.setMirrorMode(config.mirrorMode) }
        }
    }

    override fun setOnDepthFrameCallback(onDepthFrame: ((depth: Depth) -> Unit)?) {
        delegate.onDepthData = onDepthFrame?.let { callback ->
            { depthData, timestampValue, timestampScale, connection ->
                // `depthData` is a +1 reference, owned by the Depth from now on
                val depthData = checkNotNull(depthData)
                // Prepare Depth Frame + Metadata
                val metadata = MediaSampleMetadata.relativeToOutput(
                    timestamp = CMTimeMake(timestampValue, timestampScale),
                    bufferOrientation = checkNotNull(connection).bufferOrientation,
                    isBufferMirrored = connection.videoMirrored,
                    mirrorMode = mirrorMode,
                    outputOrientation = outputOrientation,
                )
                val depth = IosDepth(depthData = depthData, metadata = metadata)
                // Call the Depth callback synchronously
                callback(depth)
            }
        }
    }

    override fun setOnDepthFrameDroppedCallback(onDepthFrameDropped: ((reason: FrameDroppedReason) -> Unit)?) {
        delegate.onDepthDataDropped = onDepthFrameDropped?.let { callback ->
            { avReason -> callback(frameDroppedReasonOf(avReason)) }
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(0, 1)) return
        // Stop receiving Depth frames: clear the callbacks and detach the delegate.
        delegate.onDepthData = null
        delegate.onDepthDataDropped = null
        output.setDelegate(null, callbackQueue = null)
    }

    override fun toString(): String = "CameraDepthFrameOutput($output)"

    private companion object {
        const val QUEUE_LABEL = "me.nathanfallet.visioncamera.depth"
    }
}
