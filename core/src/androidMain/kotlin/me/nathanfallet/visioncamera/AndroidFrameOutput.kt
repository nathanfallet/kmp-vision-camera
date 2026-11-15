package me.nathanfallet.visioncamera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector

/**
 * The Android [CameraFrameOutput], backed by a CameraX [ImageAnalysis] use-case.
 */
internal class AndroidFrameOutput(
    private val options: FrameOutputOptions,
) : CameraFrameOutput, NativeCameraOutput {
    private val executor = IdentifiableExecutor("me.nathanfallet.visioncamera.frame")
    private val lock = Any()

    override val mediaType: MediaType = MediaType.VIDEO

    override val thread: NativeThread by lazy { AndroidNativeThread(executor) }

    override var outputOrientation: CameraOrientation = CameraOrientation.UP
        set(value) {
            field = value
            imageAnalysis?.targetRotation = value.surfaceRotation
        }

    override val currentResolution: Size?
        get() = imageAnalysis?.resolutionInfo?.resolution?.toSize()

    @Volatile
    override var mirrorMode: MirrorMode = MirrorMode.AUTO
        private set

    @Volatile
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var onFrame: ((Frame) -> Unit)? = null

    override fun createUseCase(
        mirrorMode: MirrorMode,
        config: NativeCameraOutput.Config,
    ): NativeCameraOutput.PreparedUseCase {
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionFilter { sizes, _ ->
                // Limitation: `enablePreviewSizedOutputBuffers` is not taken into account here.
                sizes.sortedByClosestTo(options.targetResolution.toAndroidSize())
            }.build()

        val imageAnalysis = ImageAnalysis.Builder().apply {
            // Target Resolution
            setResolutionSelector(resolutionSelector)

            // Configuration
            setAllowDroppingLateFrames(options.dropFramesWhileBusy)

            // Set current CameraOrientation
            setTargetRotation(outputOrientation.surfaceRotation)
            setOutputImageRotationEnabled(options.enablePhysicalBufferRotation)

            when (options.pixelFormat) {
                TargetVideoPixelFormat.YUV -> {
                    // Use YUV_420_888 (CPU)
                    setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                }
                TargetVideoPixelFormat.RGB -> {
                    // Use RGBA_8888 (CPU + extra conversion)
                    setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                }
                TargetVideoPixelFormat.NATIVE -> {
                    // Use PRIVATE (GPU)
                    if (options.enablePhysicalBufferRotation) {
                        throw VisionCameraException(
                            "Cannot enable physical buffer rotation when the pixel format is NATIVE! " +
                                "Set `enablePhysicalBufferRotation = false` to use GPU buffers, or use another " +
                                "pixel format if physical buffer rotation is necessary.",
                        )
                    }
                    setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_PRIVATE)
                }
            }
        }.build()

        return NativeCameraOutput.PreparedUseCase(imageAnalysis) {
            this.mirrorMode = mirrorMode
            this.imageAnalysis = imageAnalysis
            updateAnalyzer()
        }
    }

    /**
     * Sets or removes the ImageAnalyzer, depending on `onFrame`.
     */
    private fun updateAnalyzer() = synchronized(lock) {
        val imageAnalysis = imageAnalysis ?: return@synchronized
        if (onFrame != null) {
            imageAnalysis.setAnalyzer(executor) { image ->
                // The callback might have been removed in the meantime.
                val onFrame = onFrame
                if (onFrame == null) {
                    image.close()
                    return@setAnalyzer
                }
                // This represents the Image's orientation relative to the Frame Output. If
                // `enablePhysicalBufferRotation` is true, it will always be `UP` - otherwise it will be whatever
                // sensor orientation the Hardware uses, relative to current target orientation.
                val frame = AndroidFrame(image, image.orientation, mirrorMode == MirrorMode.ON)
                deliver(frame, onFrame)
            }
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deliver(frame: Frame, onFrame: (Frame) -> Unit) {
        try {
            onFrame(frame)
        } catch (error: Throwable) {
            // The pipeline stalls if a Frame is never disposed.
            frame.dispose()
            throw error
        }
    }

    override fun setOnFrameCallback(onFrame: ((frame: Frame) -> Unit)?) {
        this.onFrame = onFrame
        updateAnalyzer()
    }

    override fun setOnFrameDroppedCallback(onFrameDropped: ((reason: FrameDroppedReason) -> Unit)?) {
        // Limitation: CameraX does not have a way to figure out if a Frame has been dropped or not.
    }
}
