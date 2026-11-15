package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector

/**
 * The Android [CameraDepthFrameOutput], backed by a CameraX [ImageAnalysis] use-case streaming
 * [android.graphics.ImageFormat.DEPTH16] images.
 */
@SuppressLint("RestrictedApi")
internal class AndroidDepthFrameOutput(
    private val options: DepthFrameOutputOptions,
) : CameraDepthFrameOutput, NativeCameraOutput {
    private val executor = IdentifiableExecutor("me.nathanfallet.visioncamera.depth")
    private val lock = Any()

    override val mediaType: MediaType = MediaType.DEPTH

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
    private var onDepthFrame: ((Depth) -> Unit)? = null

    override fun createUseCase(
        mirrorMode: MirrorMode,
        config: NativeCameraOutput.Config,
    ): NativeCameraOutput.PreparedUseCase {
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionFilter { sizes, _ ->
                // Limitation: `enablePreviewSizedOutputBuffers` is not taken into account here.
                sizes.sortedByClosestTo(options.targetResolution.toAndroidSize())
            }.build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(outputOrientation.surfaceRotation)
            .setOutputImageRotationEnabled(options.enablePhysicalBufferRotation)
            .setAllowDroppingLateFrames(options.dropFramesWhileBusy)
            .setImageReaderProxyProvider { width, height, _, queueDepth, usage ->
                // Limitation: Use CameraX' default ImageAnalysis once they support DEPTH data streaming.
                DepthImageReaderProxy(width, height, queueDepth, usage)
            }.build()

        return NativeCameraOutput.PreparedUseCase(imageAnalysis) {
            this.mirrorMode = mirrorMode
            this.imageAnalysis = imageAnalysis
            updateAnalyzer()
        }
    }

    /**
     * Sets or removes the ImageAnalyzer, depending on `onDepthFrame`.
     */
    private fun updateAnalyzer() = synchronized(lock) {
        val imageAnalysis = imageAnalysis ?: return@synchronized
        if (onDepthFrame != null) {
            imageAnalysis.setAnalyzer(executor) { image ->
                // The callback might have been removed in the meantime.
                val onDepthFrame = onDepthFrame
                if (onDepthFrame == null) {
                    image.close()
                    return@setAnalyzer
                }
                // This represents the Image's orientation relative to the Depth Output. If
                // `enablePhysicalBufferRotation` is true, it will always be `UP` - otherwise it will be whatever
                // sensor orientation the Hardware uses, relative to current target orientation.
                val depth = AndroidDepth(image, image.orientation, mirrorMode == MirrorMode.ON)
                deliver(depth, onDepthFrame)
            }
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deliver(depth: Depth, onDepthFrame: (Depth) -> Unit) {
        try {
            onDepthFrame(depth)
        } catch (error: Throwable) {
            // The pipeline stalls if a Depth is never disposed.
            depth.dispose()
            throw error
        }
    }

    override fun setOnDepthFrameCallback(onDepthFrame: ((depth: Depth) -> Unit)?) {
        this.onDepthFrame = onDepthFrame
        updateAnalyzer()
    }

    override fun setOnDepthFrameDroppedCallback(onDepthFrameDropped: ((reason: FrameDroppedReason) -> Unit)?) {
        // Limitation: CameraX does not have a way to figure out if a Frame has been dropped or not.
    }
}
