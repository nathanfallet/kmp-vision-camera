package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureDataOutputSynchronizer

/**
 * The iOS [CameraOutputSynchronizer], backed by an [AVCaptureDataOutputSynchronizer] (port of V5's
 * `HybridCameraOutputSynchronizer.swift`).
 *
 * Unlike V5, the callbacks can be set from any thread; they always run on [thread].
 *
 * @throws VisionCameraException If an output is not an iOS output, if there are less than 2 outputs, or if not all
 * outputs are connected to the Camera yet.
 */
internal class IosCameraOutputSynchronizer(
    override val outputs: List<CameraOutput>,
) : CameraOutputSynchronizer {
    // Like V5, we don't make sure yet that all outputs are running on the same DispatchQueue.
    private val queue = createSerialQueue(QUEUE_LABEL)
    private val delegate = SynchronizedOutputDelegate()
    private val synchronizer: AVCaptureDataOutputSynchronizer

    override val thread: NativeThread by lazy { IosNativeThread(queue = queue, label = QUEUE_LABEL) }

    init {
        val avOutputs = outputs.map { output ->
            (output as? NativeCameraOutput)?.output
                ?: throw VisionCameraException("Output $output is not of type `NativeCameraOutput`!")
        }
        if (avOutputs.size < 2) {
            throw VisionCameraException("Cannot create CameraOutputSynchronizer with less than 2 outputs!")
        }
        val allConnected = avOutputs.all { it.connections.isNotEmpty() }
        if (!allConnected) {
            throw VisionCameraException(
                "Cannot create CameraOutputSynchronizer when not all of the given outputs are connected to the Camera!",
            )
        }
        synchronizer = AVCaptureDataOutputSynchronizer(dataOutputs = avOutputs)

        // Set delegate
        synchronizer.setDelegate(delegate.native, queue = queue)
    }

    override fun setOnFramesCallback(onFrames: ((frames: List<FrameOrDepth>) -> Unit)?) {
        delegate.onFrames = onFrames
    }

    override fun setOnFrameDroppedCallback(
        onFrameDropped: ((frameType: MediaType, reason: FrameDroppedReason) -> Unit)?,
    ) {
        delegate.onFrameDropped = onFrameDropped?.let { callback ->
            { mediaType, avReason -> callback(mediaType, frameDroppedReasonOf(avReason)) }
        }
    }

    override fun toString(): String = "CameraOutputSynchronizer($outputs)"

    private companion object {
        const val QUEUE_LABEL = "me.nathanfallet.visioncamera.synchronizer"
    }
}
