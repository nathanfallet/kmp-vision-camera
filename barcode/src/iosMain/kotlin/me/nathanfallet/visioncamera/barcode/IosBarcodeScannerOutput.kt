package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.MediaType
import me.nathanfallet.visioncamera.NativeCameraOutput
import me.nathanfallet.visioncamera.OutputConfiguration
import me.nathanfallet.visioncamera.ResolutionRule
import me.nathanfallet.visioncamera.Size
import me.nathanfallet.visioncamera.StreamType
import me.nathanfallet.visioncamera.VisionCameraException
import me.nathanfallet.visioncamera.barcode.extensions.isIOSAtLeast
import me.nathanfallet.visioncamera.inputStreamResolution
import me.nathanfallet.visioncamera.toUIImageOrientation
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoStabilizationModeOff
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile

/**
 * The iOS Barcode Scanner output: an [AVCaptureVideoDataOutput] whose frames are scanned with Apple Vision (port of
 * V5's `HybridBarcodeScannerOutput.swift`, which scans with ML Kit).
 *
 * Like V5, frames arriving while a scan is running are skipped, and the callbacks are called on the main thread
 * (where ML Kit calls its completion handlers).
 */
internal class IosBarcodeScannerOutput(
    options: BarcodeScannerOutputOptions,
) : NativeCameraOutput {
    private val detector = VisionBarcodeDetector(options.barcodeFormats)
    private val onBarcodeScanned = options.onBarcodeScanned
    private val onError = options.onError

    /** The queue the [delegate] receives frames on. */
    private val queue = dispatch_queue_create("me.nathanfallet.visioncamera.barcodescanner", null)

    /** The queue barcodes are detected on (ML Kit's processing queue in V5). */
    private val processingQueue = dispatch_queue_create("me.nathanfallet.visioncamera.barcodescanner.processing", null)

    private val delegate = BarcodeScannerDelegate(onSampleBuffer = { buffer -> scan(buffer) })

    /** Whether a frame is being scanned. Set on [queue], reset on the main thread. */
    @Volatile
    private var isScanning = false

    private val isDisposed = AtomicInt(0)

    override val output: AVCaptureVideoDataOutput = AVCaptureVideoDataOutput()
    override val requiresAudioInput: Boolean = false
    override val requiresDepthFormat: Boolean = false
    override val mediaType: MediaType = MediaType.VIDEO

    @Volatile
    override var outputOrientation: CameraOrientation = CameraOrientation.UP

    private val videoConnection: AVCaptureConnection?
        get() = output.connectionWithMediaType(AVMediaTypeVideo)

    override val currentResolution: Size?
        get() = videoConnection?.inputStreamResolution

    override val streamType: StreamType = StreamType.VIDEO

    // Like V5 (which wonders whether it is the best size for ML), a 720p stream.
    override val targetResolution: ResolutionRule = ResolutionRule.ClosestTo(Size(width = 720, height = 1280))

    init {
        // set delegate
        output.setSampleBufferDelegate(delegate, queue = queue)
        output.alwaysDiscardsLateVideoFrames = true
        if (isIOSAtLeast(major = 17) && options.outputResolution != BarcodeScannerOutputResolution.FULL) {
            output.automaticallyConfiguresOutputBufferDimensions = false
            output.deliversPreviewSizedOutputBuffers = true
        }
    }

    private fun scan(buffer: CMSampleBufferRef) {
        if (isScanning) return

        // prepare the frame
        isScanning = true
        val orientation = outputOrientation.toUIImageOrientation()
        CFRetain(buffer)
        // start scanning
        dispatch_async(processingQueue) {
            val result = runCatching {
                val pixelBuffer = CMSampleBufferGetImageBuffer(buffer)
                    ?: throw VisionCameraException("Failed to get the CVPixelBuffer of the CMSampleBuffer!")
                detector.detect(pixelBuffer, orientation)
            }
            CFRelease(buffer)
            dispatch_async(dispatch_get_main_queue()) {
                isScanning = false
                // A scan still running when this output is disposed is dropped.
                if (isDisposed.value != 0) return@dispatch_async
                result
                    .onSuccess { barcodes -> onBarcodeScanned(barcodes) }
                    .onFailure { error -> onError(error) }
            }
        }
    }

    override fun configure(config: OutputConfiguration) {
        val connection = videoConnection ?: return
        connection.preferredVideoStabilizationMode = AVCaptureVideoStabilizationModeOff
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(0, 1)) return
        // Stop receiving frames by detaching the delegate.
        output.setSampleBufferDelegate(null, queue = null)
    }

    override fun toString(): String = "BarcodeScannerOutput($output)"
}
