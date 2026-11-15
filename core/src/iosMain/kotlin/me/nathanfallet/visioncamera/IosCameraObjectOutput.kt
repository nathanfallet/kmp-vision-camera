package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVMediaTypeMetadataObject
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.AtomicInt

/**
 * The iOS [CameraObjectOutput], scanning codes, faces and bodies with an [AVCaptureMetadataOutput] (port of V5's
 * `HybridCameraObjectOutput.swift`).
 */
internal class IosCameraObjectOutput(
    private val options: ObjectOutputOptions,
) : CameraObjectOutput, NativeCameraOutput {
    private val queue = dispatch_queue_create("me.nathanfallet.visioncamera.object-scanner", null)
    private val delegate = MetadataDelegate()
    private val isDisposed = AtomicInt(0)

    override val mediaType: MediaType = MediaType.METADATA
    override val output: AVCaptureMetadataOutput = AVCaptureMetadataOutput()
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = false

    private val metadataConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeMetadataObject)

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            val connection = metadataConnection ?: return
            runCatching { connection.setOrientation(value) }
        }

    override val currentResolution: Size?
        get() = metadataConnection?.inputStreamResolution

    override val streamType: StreamType = StreamType.VIDEO

    override val targetResolution: ResolutionRule = ResolutionRule.Any

    init {
        output.setMetadataObjectsDelegate(delegate, queue = queue)
    }

    override fun configure(config: OutputConfiguration) {
        val connection = metadataConnection ?: return
        runCatching { connection.setOrientation(outputOrientation) }
        runCatching { connection.setMirrorMode(config.mirrorMode) }

        // Enable all the types that are supported
        val availableObjectTypes = output.availableMetadataObjectTypes.filterIsInstance<String>()
        output.metadataObjectTypes = options.enabledObjectTypes
            .mapNotNull { it.toAVMetadataObjectType() }
            .filter { it in availableObjectTypes }
    }

    override fun setOnObjectsScannedCallback(onObjectsScanned: ((objects: List<ScannedObject>) -> Unit)?) {
        delegate.onMetadataObjects = onObjectsScanned?.let { callback ->
            { objects -> callback(objects.map { createScannedObject(it) }) }
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(0, 1)) return
        // Stop receiving scanned objects: clear the callback and detach the delegate.
        delegate.onMetadataObjects = null
        output.setMetadataObjectsDelegate(null, queue = null)
    }

    override fun toString(): String = "CameraObjectOutput($output)"
}
