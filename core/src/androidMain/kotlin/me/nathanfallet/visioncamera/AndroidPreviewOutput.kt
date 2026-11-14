package me.nathanfallet.visioncamera

import androidx.camera.core.Preview

/**
 * The Android [CameraPreviewOutput], backed by a CameraX [Preview] use-case.
 *
 * It streams into the [Preview.SurfaceProvider] of a connected [PreviewView].
 */
internal class AndroidPreviewOutput : CameraPreviewOutput, NativeCameraOutput {
    private var preview: Preview? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null

    override val mediaType: MediaType = MediaType.VIDEO

    // Setting orientation in a Preview output does nothing, it is controlled by the PreviewView.
    override var outputOrientation: CameraOrientation
        get() = CameraOrientation.UP
        set(_) = Unit

    override val mirrorMode: MirrorMode
        get() = preview?.let { mirrorModeFromCameraX(it.mirrorMode) } ?: MirrorMode.AUTO

    override val currentResolution: Size? get() = preview?.resolutionInfo?.resolution?.toSize()

    /** Connects the given [surfaceProvider], must be called on the main thread. */
    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        this.surfaceProvider = surfaceProvider
        preview?.surfaceProvider = surfaceProvider
    }

    /** Disconnects the given [surfaceProvider] if it is the connected one, must be called on the main thread. */
    fun removeSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        if (this.surfaceProvider == surfaceProvider) {
            this.surfaceProvider = null
            preview?.surfaceProvider = null
        }
    }

    override fun createUseCase(mirrorMode: MirrorMode, config: NativeCameraOutput.Config): NativeCameraOutput.PreparedUseCase {
        val preview = Preview.Builder().apply {
            setMirrorMode(mirrorMode.toCameraXMirrorMode())
            when (config.previewStabilizationMode) {
                // Unspecified: might be enabled, might be disabled.
                null, TargetStabilizationMode.Auto -> Unit
                TargetStabilizationMode.Off -> setPreviewStabilizationEnabled(false)
                // Android only knows "on", no matter if it's STANDARD, CINEMATIC, etc.
                is StabilizationMode -> setPreviewStabilizationEnabled(true)
            }
        }.build()
        return NativeCameraOutput.PreparedUseCase(preview) {
            this.preview = preview
            preview.surfaceProvider = surfaceProvider
        }
    }
}
