package me.nathanfallet.visioncamera

import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.UIKit.UIScreen
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

/**
 * The iOS [CameraPreviewOutput], backed by an [AVCaptureVideoPreviewLayer] that a [PreviewView] displays (port of
 * V5's `HybridCameraPreviewOutput.swift`).
 *
 * Its orientation always follows the interface orientation.
 */
internal class IosCameraPreviewOutput : CameraPreviewOutput, NativePreviewViewOutput {
    override val mediaType: MediaType = MediaType.VIDEO

    override val previewLayer: AVCaptureVideoPreviewLayer = AVCaptureVideoPreviewLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    private val orientationManager = InterfaceOrientationManager()
    private val isDisposed = AtomicInt(0)

    // Stops the orientation updates once this output is garbage collected (the equivalent of V5's `deinit`).
    @OptIn(ExperimentalNativeApi::class)
    @Suppress("unused")
    private val cleaner: Cleaner = createCleaner(orientationManager) { it.stopOrientationUpdates() }

    // Setting orientation on a Preview output does nothing, as its orientation is always driven by the interface
    // orientation.
    override var outputOrientation: CameraOrientation
        get() = previewLayer.connection?.orientation ?: CameraOrientation.UP
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    override val currentResolution: Size?
        get() = previewLayer.connection?.inputStreamResolution

    override val streamType: StreamType = StreamType.VIDEO

    /**
     * The Preview can never display more pixels than the screen has, so any Format at least as large as the screen
     * (`>=`) is a perfect match - hence [ResolutionRule.Min]. This is only a _bias_; a slightly smaller Format only
     * gets a small penalty (it is upscaled on-screen), so other outputs (e.g. a video recording with an explicit
     * target resolution) can still win the resolution negotiation.
     *
     * We use the screen size instead of the actual view size here because the view might not even be laid out yet at
     * the time the resolution negotiation runs.
     */
    override val targetResolution: ResolutionRule
        get() {
            val screen = UIScreen.mainScreen
            val scale = screen.scale
            val screenSizePx = screen.bounds.useContents {
                Size(width = (size.width * scale).toInt(), height = (size.height * scale).toInt())
            }
            return ResolutionRule.Min(screenSizePx)
        }

    init {
        // Capture the layer only (not `this`), so this output can still be garbage collected.
        val layer = previewLayer
        orientationManager.startOrientationUpdates { orientation ->
            layer.connection?.let { connection ->
                runCatching { connection.setOrientation(orientation) }
            }
        }
    }

    override fun configure(config: OutputConfiguration) {
        val connection = previewLayer.connection ?: return
        runCatching { connection.setMirrorMode(config.mirrorMode) }
        orientationManager.currentOrientation?.let { orientation ->
            runCatching { connection.setOrientation(orientation) }
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(0, 1)) return
        // Like V5's `deinit`, stop following the interface orientation.
        orientationManager.stopOrientationUpdates()
    }

    override fun toString(): String = "CameraPreviewOutput($previewLayer)"
}
