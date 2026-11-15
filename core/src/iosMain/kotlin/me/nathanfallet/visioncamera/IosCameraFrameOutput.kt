package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.Foundation.NSNumber
import kotlin.concurrent.Volatile

/**
 * The iOS [CameraFrameOutput], streaming the sample buffers of an [AVCaptureVideoDataOutput] as [Frame]s (port of
 * V5's `HybridCameraFrameOutput.swift`).
 *
 * Unlike V5, where the callbacks must be set on the output's [thread] (a worklet constraint), they can be set from any
 * thread; they always run on [thread].
 */
internal class IosCameraFrameOutput(
    private val options: FrameOutputOptions,
) : CameraFrameOutput, NativeCameraOutput {
    private val delegate = FrameDelegate()
    private val queue = createSerialQueue(QUEUE_LABEL)

    // Like V5, `configure(...)` sets it on the session queue while Frames read it on the Frame queue, so an old Frame
    // might read a newer `mirrorMode`.
    @Volatile
    private var mirrorMode: MirrorMode = MirrorMode.AUTO

    override val mediaType: MediaType = MediaType.VIDEO
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = false
    override val output: AVCaptureVideoDataOutput = AVCaptureVideoDataOutput()

    override val thread: NativeThread by lazy { IosNativeThread(queue = queue, label = QUEUE_LABEL) }

    private val videoConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeVideo)

    @Volatile
    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            val connection = videoConnection ?: return
            if (options.enablePhysicalBufferRotation) {
                runCatching { connection.setOrientation(value) }
            }
        }

    override val currentResolution: Size?
        get() = videoConnection?.inputStreamResolution

    override val streamType: StreamType = StreamType.VIDEO

    override val targetResolution: ResolutionRule
        get() = ResolutionRule.ClosestTo(options.targetResolution)

    init {
        // Set up our `delegate`
        output.setSampleBufferDelegate(delegate, queue = queue)
        // Configure `videoSettings`
        output.videoSettings = videoSettingsForPixelFormat(options.pixelFormat)
        // If the pipeline stalls, drop frames to avoid blowing up RAM
        output.alwaysDiscardsLateVideoFrames = options.dropFramesWhileBusy
        // We configure the video resolution ourselves, we don't want to downscale here by default.
        output.automaticallyConfiguresOutputBufferDimensions = false
        if (isIOSAtLeast(major = 26) && output.deferredStartSupported) {
            // Deferred start allows the Session to delay this output's startup in favor of preview-related outputs to
            // make preview appear faster.
            output.deferredStartEnabled = options.allowDeferredStart
        }
        if (isIOSAtLeast(major = 17) && options.enablePreviewSizedOutputBuffers) {
            output.deliversPreviewSizedOutputBuffers = true
        }
        if (isIOSAtLeast(major = 26)) {
            // We don't need HDR metadata, as that's only useful for encoders.
            output.preservesDynamicHDRMetadata = false
        }
    }

    override fun configure(config: OutputConfiguration) {
        // Like V5, this is not attached to the `connection`, so there may be race conditions where we read
        // `mirrorMode` for an old Frame.
        mirrorMode = config.mirrorMode

        val connection = videoConnection ?: return
        if (options.enablePhysicalBufferRotation) {
            // Pipeline should physically rotate buffers on the `connection`
            runCatching { connection.setOrientation(outputOrientation) }
            runCatching { connection.setMirrorMode(config.mirrorMode) }
        }
        if (options.enableCameraMatrixDelivery) {
            // Pipeline should deliver camera matrix (via attachment on `CMSampleBuffer`)
            if (connection.cameraIntrinsicMatrixDeliverySupported) {
                connection.cameraIntrinsicMatrixDeliveryEnabled = true
            }
        }
    }

    private fun videoSettingsForPixelFormat(targetPixelFormat: TargetVideoPixelFormat): Map<Any?, Any?> =
        when (val pixelFormat = targetPixelFormat.toCVPixelFormatType()) {
            // Empty dictionary means "choose device-native format" (most efficient)
            TargetCVPixelFormat.Native -> emptyMap()
            // Use a specific format (e.g. 32 BGRA)
            is TargetCVPixelFormat.Specific -> mapOf(
                kCVPixelBufferPixelFormatTypeKey.toKotlinString() to NSNumber(unsignedInt = pixelFormat.format),
            )
        }

    override fun setOnFrameCallback(onFrame: ((frame: Frame) -> Unit)?) {
        delegate.onFrame = onFrame?.let { callback ->
            { sampleBuffer, timestamp, bufferOrientation, isBufferMirrored ->
                // Prepare Frame + Metadata
                val metadata = MediaSampleMetadata.relativeToOutput(
                    timestamp = timestamp,
                    bufferOrientation = bufferOrientation,
                    isBufferMirrored = isBufferMirrored,
                    mirrorMode = mirrorMode,
                    outputOrientation = outputOrientation,
                )
                val frame = IosFrame(buffer = sampleBuffer, metadata = metadata)
                // Call the Frame callback synchronously
                callback(frame)
            }
        }
    }

    override fun setOnFrameDroppedCallback(onFrameDropped: ((reason: FrameDroppedReason) -> Unit)?) {
        delegate.onFrameDropped = onFrameDropped?.let { callback ->
            { sampleBuffer ->
                frameDroppedReasonOfSampleBuffer(sampleBuffer)?.let(callback)
            }
        }
    }

    override fun toString(): String = "CameraFrameOutput($output)"

    private companion object {
        const val QUEUE_LABEL = "me.nathanfallet.visioncamera.frame"
    }
}
