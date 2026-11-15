package me.nathanfallet.visioncamera.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.google.mlkit.vision.barcode.BarcodeScanning
import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.MediaType
import me.nathanfallet.visioncamera.MirrorMode
import me.nathanfallet.visioncamera.NativeCameraOutput
import me.nathanfallet.visioncamera.Size
import me.nathanfallet.visioncamera.surfaceRotation
import me.nathanfallet.visioncamera.toSize
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android Barcode Scanner output, an ML Kit barcode scanner fed by a CameraX [ImageAnalysis] use-case (port of
 * V5's `HybridBarcodeScannerOutput`).
 *
 * Frames arriving while the previous one is still being scanned are dropped. The [Barcode] coordinates are relative
 * to the upright frame (ML Kit is given the frame's `rotationDegrees`), like V5.
 */
internal class AndroidBarcodeScannerOutput(
    private val options: BarcodeScannerOutputOptions,
) : NativeCameraOutput, AutoCloseable {
    override val mediaType: MediaType = MediaType.VIDEO

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            imageAnalysis?.targetRotation = value.surfaceRotation
        }

    override val currentResolution: Size?
        get() = imageAnalysis?.resolutionInfo?.resolution?.toSize()

    override val mirrorMode: MirrorMode = MirrorMode.AUTO

    @Volatile
    private var imageAnalysis: ImageAnalysis? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        // Daemon thread, so an output that is never disposed never keeps the process alive.
        Thread(runnable, "me.nathanfallet.visioncamera.barcode").also { it.isDaemon = true }
    }
    private val scanner = BarcodeScanning.getClient(mlBarcodeScannerOptions(options.barcodeFormats))
    private val isBusy = AtomicBoolean(false)

    override fun createUseCase(
        mirrorMode: MirrorMode,
        config: NativeCameraOutput.Config,
    ): NativeCameraOutput.PreparedUseCase {
        val resolutionStrategy = when (options.outputResolution) {
            BarcodeScannerOutputResolution.FULL -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            BarcodeScannerOutputResolution.PREVIEW -> ResolutionStrategy(
                android.util.Size(RECOMMENDED_WIDTH, RECOMMENDED_HEIGHT),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
        }
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setOutputImageRotationEnabled(false)
            .setTargetRotation(outputOrientation.surfaceRotation)
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        return NativeCameraOutput.PreparedUseCase(imageAnalysis) {
            this.imageAnalysis = imageAnalysis
            imageAnalysis.setAnalyzer(executor, ::analyze)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun analyze(imageProxy: ImageProxy) {
        if (!isBusy.compareAndSet(false, true)) {
            // The pipeline is busy: drop this frame.
            imageProxy.close()
            return
        }
        try {
            // Limitation (as in V5): the MirrorMode is not taken into account.
            scanner.process(imageProxy.toInputImage())
                .addOnSuccessListener { barcodes -> options.onBarcodeScanned(barcodes.map { AndroidBarcode(it) }) }
                .addOnFailureListener { error -> options.onError(error) }
                .addOnCompleteListener {
                    imageProxy.close()
                    isBusy.set(false)
                }
        } catch (error: Throwable) {
            imageProxy.close()
            isBusy.set(false)
            options.onError(error)
        }
    }

    /**
     * Disposes this output, releasing the ML Kit scanner and the analysis thread. It must not be used afterwards.
     */
    fun dispose() {
        imageAnalysis?.clearAnalyzer()
        scanner.close()
        executor.shutdown()
    }

    override fun close() {
        dispose()
    }

    private companion object {
        // The resolution recommended by ML Kit for barcode scanning, used for PREVIEW.
        const val RECOMMENDED_WIDTH = 1280
        const val RECOMMENDED_HEIGHT = 720
    }
}
