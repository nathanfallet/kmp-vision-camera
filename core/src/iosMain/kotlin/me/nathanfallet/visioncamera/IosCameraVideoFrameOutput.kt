@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureInputPort
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeHEVC
import platform.CoreMedia.CMClockRef
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSLock
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * The iOS persistent [CameraVideoOutput], recording sample buffers of an [AVCaptureVideoDataOutput] (and of a
 * separate audio [AudioSession]) with an `AVAssetWriter` (port of V5's `HybridCameraVideoFrameOutput.swift`). Used
 * when [VideoOutputOptions.enablePersistentRecorder] is `true`, since `AVCaptureMovieFileOutput` does not allow
 * flipping the Camera while recording.
 */
internal class IosCameraVideoFrameOutput(
    private val options: VideoOutputOptions,
) : CameraVideoOutput, NativeCameraOutput, RecorderDelegate {
    private val delegate = FrameDelegate()
    private val queue = createSerialQueue("me.nathanfallet.visioncamera.video-frame")
    private val videoQueue = createSerialQueue("me.nathanfallet.visioncamera.video-frame.video", QOS_CLASS_UTILITY)
    private var audioSession: AudioSession? = null
    override val mediaType: MediaType = MediaType.VIDEO
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = false
    override val output: AVCaptureVideoDataOutput = AVCaptureVideoDataOutput()
    private val recordersLock = NSLock()
    private val recorders = mutableListOf<WeakReference<IosFrameRecorder>>()
    private val fileType: RecorderFileType = options.fileType
    private val isDisposed = AtomicInt(0)

    override val streamType: StreamType = StreamType.VIDEO

    override val targetResolution: ResolutionRule
        get() = ResolutionRule.ClosestTo(options.targetResolution)

    private val videoConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeVideo)

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            val connection = videoConnection ?: return
            runCatching { connection.setOrientation(value) }
        }

    override val currentResolution: Size?
        get() = videoConnection?.inputStreamResolution

    // Like V5, a custom `targetBitRate` is not supported yet on this output.
    init {
        // Set up our `delegate`
        output.setSampleBufferDelegate(delegate, queue = videoQueue)
        // Set the pixel format to the device native format.
        output.videoSettings = emptyMap<Any?, Any?>()
        // Queue up Frames if there are any hiccups in AVAssetWriter so we don't lose Frames.
        output.alwaysDiscardsLateVideoFrames = false
        // We configure the video resolution ourselves, we don't want to downscale here.
        output.automaticallyConfiguresOutputBufferDimensions = false
        if (isIOSAtLeast(major = 26)) {
            // We only use this output for recording, allowing it to start deferred makes the session start faster.
            if (output.deferredStartSupported) output.deferredStartEnabled = true
            // Allow capturing HDR
            output.preservesDynamicHDRMetadata = true
        }
        // Set the delegate to append to the Recorder
        delegate.onFrame = { buffer, _, _, _ -> onFrame(buffer, TrackType.VIDEO) }
    }

    override fun configure(config: OutputConfiguration) {
        // Like V5, we physically rotate buffers instead of setting the orientation via video metadata flags: we need to
        // support switching Camera devices on the fly, which rotates buffer later on.
        val connection = videoConnection ?: return
        runCatching { connection.setMirrorMode(config.mirrorMode) }
        runCatching { connection.setOrientation(outputOrientation) }
    }

    override fun getSupportedVideoCodecs(): List<VideoCodec> {
        if (videoConnection == null) {
            throw VisionCameraException(
                "Cannot call `getSupportedVideoCodecs()` when VideoOutput is not yet connected to the CameraSession!",
            )
        }
        return output.availableVideoCodecTypesForAssetWriterWithOutputFileType(fileType.toAVFileType())
            .map { videoCodecOf(it as? String) }
    }

    override suspend fun setOutputSettings(settings: VideoOutputSettings) = queue.await {
        val currentSettings: MutableMap<Any?, Any?> = output.videoSettings.toMutableMap()

        // codec={..}
        settings.codec?.let { codec ->
            val avCodec = codec.toAVVideoCodecType()
            val availableCodecs = output.availableVideoCodecTypesForAssetWriterWithOutputFileType(
                fileType.toAVFileType(),
            )
            if (avCodec !in availableCodecs) {
                throw VisionCameraException("VideoOutput does not support the codec \"$codec\"!")
            }
            currentSettings[AVVideoCodecKey] = avCodec
        }

        output.videoSettings = currentSettings
    }

    override suspend fun createRecorder(settings: RecorderSettings): Recorder = queue.await {
        // 1. Create AVAssetWriter Recorder
        val recorder = IosFrameRecorder(
            orientation = CameraOrientation.UP,
            masterClock = getMasterClock(),
            fileType = fileType,
            settings = settings,
            delegate = this,
        )
        // 2. Initialize its video track immediately
        val videoSettings = output.recommendedVideoSettingsForAssetWriterWithOutputFileType(fileType.toAVFileType())
            ?: throw VisionCameraException("Cannot initialize Recorder - no available video settings were found!")
        recorder.initializeVideoTrack(videoSettings)

        // 3. Set video metadata
        if (isIOSAtLeast(major = 26)) {
            // 3.1.a. Set video timescale if available. This avoids audio/video sync-drift in super long videos.
            recorder.setTimescale(output.recommendedMediaTimeScaleForAssetWriter)
            val metadata = output.recommendedMovieMetadataForVideoCodecType(
                getCurrentVideoCodec(),
                assetWriterOutputFileType = fileType.toAVFileType(),
            )
            // 3.2. Set metadata if available
            if (metadata != null) recorder.setMetadata(metadata.filterIsInstance<AVMetadataItem>())
        } else {
            // 3.1.b. Set metadata before iOS 26.0
            recorder.setMetadata(emptyList())
        }

        // 4. If we enable audio, also initialize the audio track
        if (options.enableAudio) {
            val audioSession = getOrCreateAudioSession()
            val audioSettings = audioSession.output.recommendedAudioSettingsForAssetWriterWithOutputFileType(
                fileType.toAVFileType(),
            )
            val audioFormat = audioSession.input.device.activeFormat.formatDescription
                ?: throw VisionCameraException("The microphone does not have a format description!")
            recorder.initializeAudioTrack(audioSettings, audioFormat)
        }

        // 5. Add it to our state and return
        withRecorders { it.add(WeakReference(recorder)) }
        recorder
    }

    override fun onRecorderWillStart() {
        audioSession?.start()
    }

    override fun onRecorderDidStop() {
        val audioSession = audioSession ?: return
        val hasActiveRecorders = activeRecorders().any { it.isRecording }
        if (!hasActiveRecorders) audioSession.stop()
    }

    private fun getCurrentVideoCodec(): String? =
        output.videoSettings[AVVideoCodecKey] as? String ?: AVVideoCodecTypeHEVC

    @Suppress("ThrowsCount")
    private fun getMasterClock(): CMClockRef {
        val connection = videoConnection
            ?: throw VisionCameraException("The VideoOutput is not yet connected to the CameraSession!")
        val inputPort = connection.inputPorts.firstOrNull() as? AVCaptureInputPort
            ?: throw VisionCameraException("The VideoOutput is not yet connected to an input!")
        return inputPort.clock
            ?: throw VisionCameraException("The VideoOutput's connected input device does not have a master clock!")
    }

    private fun getOrCreateAudioSession(): AudioSession {
        // We already created an Audio AVCaptureSession, return it
        audioSession?.let { return it }
        // 1. Create session
        val audioSession = AudioSession()
        // 2. Add on frame listener
        audioSession.setOnFrameListener { buffer -> onFrame(buffer, TrackType.AUDIO) }
        // 3. Return it!
        this.audioSession = audioSession
        return audioSession
    }

    private fun onFrame(buffer: CMSampleBufferRef, type: TrackType) {
        for (recorder in activeRecorders()) {
            if (recorder.isRecording) recorder.append(buffer, type)
        }
    }

    /** The recorders that are still alive (V5's `WeakArray.values()`). */
    private fun activeRecorders(): List<IosFrameRecorder> = withRecorders { recorders ->
        recorders.removeAll { it.value == null }
        recorders.mapNotNull { it.value }
    }

    private inline fun <T> withRecorders(block: (MutableList<WeakReference<IosFrameRecorder>>) -> T): T {
        recordersLock.lock()
        try {
            return block(recorders)
        } finally {
            recordersLock.unlock()
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(0, 1)) return
        // Stop receiving video buffers, and stop the separate audio capture session (which holds the microphone).
        delegate.onFrame = null
        output.setSampleBufferDelegate(null, queue = null)
        audioSession?.stop()
        audioSession = null
        withRecorders { it.clear() }
    }

    override fun toString(): String = "CameraVideoOutput($output)"
}
