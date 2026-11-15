package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeHEVC
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.key
import platform.AVFoundation.keySpace

/**
 * The iOS [CameraVideoOutput], recording with an [AVCaptureMovieFileOutput] (port of V5's
 * `HybridCameraVideoOutput.swift`). Used when [VideoOutputOptions.enablePersistentRecorder] is `false`.
 */
internal class IosCameraVideoOutput(private val options: VideoOutputOptions) : CameraVideoOutput, NativeCameraOutput {
    private val queue = createSerialQueue("me.nathanfallet.visioncamera.video")
    private val fileType: RecorderFileType = options.fileType
    override val mediaType: MediaType = MediaType.VIDEO
    override val output: AVCaptureMovieFileOutput = AVCaptureMovieFileOutput()
    override val requiresAudioInput: Boolean get() = options.enableAudio
    override val requiresDepthFormat: Boolean = false

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

    override val streamType: StreamType = StreamType.VIDEO

    override val targetResolution: ResolutionRule
        get() = ResolutionRule.ClosestTo(options.targetResolution)

    init {
        setMetadataTag(libraryTagMetadataItem())
    }

    private fun setMetadataTag(tag: AVMetadataItem) {
        // Get metadata (or empty list), and remove all items with the same key
        val metadata = output.metadata.orEmpty()
            .filterIsInstance<AVMetadataItem>()
            .filterNot { item -> item.keySpace == tag.keySpace && item.key == tag.key }
        // Add the new tag
        output.metadata = metadata + tag
    }

    override fun configure(config: OutputConfiguration) {
        val connection = videoConnection ?: return
        runCatching { connection.setOrientation(outputOrientation) }
        runCatching { connection.setMirrorMode(config.mirrorMode) }
        options.targetBitRate?.let { setBitRate(it.toInt(), connection) }
    }

    override fun getSupportedVideoCodecs(): List<VideoCodec> {
        if (videoConnection == null) {
            throw VisionCameraException(
                "Cannot call `getSupportedVideoCodecs()` when VideoOutput is not yet connected to the CameraSession!",
            )
        }
        return output.availableVideoCodecTypes.map { videoCodecOf(it as? String) }
    }

    override suspend fun setOutputSettings(settings: VideoOutputSettings) = queue.await {
        val connection = videoConnection ?: throw VisionCameraException(
            "Cannot set output settings when VideoOutput is not yet connected to the CameraSession!",
        )
        val codec = settings.codec ?: return@await
        if (codec.isRawCodec && options.targetBitRate != null) {
            throw VisionCameraException("Cannot set bit-rate when recording in a RAW codec! ($codec)")
        }
        val avCodec = codec.toAVVideoCodecType()
        val availableCodecs = output.availableVideoCodecTypes.map { it as? String }
        if (avCodec !in availableCodecs) {
            throw VisionCameraException(
                "Video codec $codec is not supported by this device! Supported codecs: $availableCodecs",
            )
        }
        val bitRate = options.targetBitRate
        if (bitRate != null) {
            setOutputSettings(bitRate = bitRate.toInt(), codec = avCodec, connection = connection)
        } else {
            output.setOutputSettings(mapOf<Any?, Any?>(AVVideoCodecKey to avCodec), forConnection = connection)
        }
    }

    override suspend fun createRecorder(settings: RecorderSettings): Recorder = queue.await {
        settings.location?.let { setMetadataTag(it.toAVMutableMetadataItem()) }
        IosVideoRecorder(videoOutput = output, queue = queue, fileType = fileType, settings = settings)
    }

    private fun setBitRate(bitRate: Int, connection: AVCaptureConnection) {
        val currentSettings = output.outputSettingsForConnection(connection)
        val currentCodec = currentSettings[AVVideoCodecKey] as? String ?: AVVideoCodecTypeHEVC
        setOutputSettings(bitRate = bitRate, codec = currentCodec, connection = connection)
    }

    private fun setOutputSettings(bitRate: Int, codec: String?, connection: AVCaptureConnection) {
        val supportedKeys = output.supportedOutputSettingsKeysForConnection(connection)
        if (AVVideoCompressionPropertiesKey !in supportedKeys) {
            Logger.error("Setting `AVVideoCompressionPropertiesKey` is not supported - cannot use custom bit-rate!")
            return
        }
        val settings = mapOf<Any?, Any?>(
            AVVideoCodecKey to codec,
            // ..the remaining AVVideoCompressionPropertiesKey values will be filled by AVFoundation
            AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(AVVideoAverageBitRateKey to bitRate),
        )
        output.setOutputSettings(settings, forConnection = connection)
    }

    // Like V5, this output holds no native resource of its own: recordings are owned (and stopped) by their `Recorder`.
    override fun dispose() = Unit

    override fun toString(): String = "CameraVideoOutput($output)"
}
