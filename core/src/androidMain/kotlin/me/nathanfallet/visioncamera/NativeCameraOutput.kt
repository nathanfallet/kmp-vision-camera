package me.nathanfallet.visioncamera

import android.util.Range
import androidx.camera.core.UseCase

/**
 * A [CameraOutput] backed by a CameraX [UseCase] (port of V5's `public/NativeCameraOutput.kt`).
 *
 * This is an extension point for plugin modules: a module (e.g. `barcode`) adds its own output to a [CameraSession]
 * by implementing [NativeCameraOutput], and the session binds the [UseCase] it creates like the built-in outputs'.
 */
interface NativeCameraOutput : CameraOutput {
    /**
     * Resolved configuration for a camera output. `null` values mean "no constraint specified, platform decides".
     *
     * This is an extension point for plugin modules, passed to [createUseCase].
     */
    data class Config(
        /** The resolved FPS range, or `null` to let the platform decide. */
        val fpsRange: Range<Int>?,
        /** The resolved preview stabilization mode, or `null` to let the platform decide. */
        val previewStabilizationMode: TargetStabilizationMode?,
        /** The resolved video stabilization mode, or `null` to let the platform decide. */
        val videoStabilizationMode: TargetStabilizationMode?,
        /** The resolved video dynamic range, or `null` to let the platform decide. */
        val videoDynamicRange: TargetDynamicRange?,
        /** Whether photo HDR should be enabled, or `null` to let the platform decide. */
        val photoHDR: Boolean?,
    )

    /**
     * A [UseCase] prepared by a [NativeCameraOutput], but not yet attached to the Camera. After it has been bound
     * with `bindToLifecycle(...)`, call [notifyAttached] to let the output know it can use the live [UseCase].
     *
     * This is an extension point for plugin modules, returned by [createUseCase]. The output captures its own typed
     * reference (e.g. an `ImageAnalysis`) in the `onAttached` closure, so no downcasting is ever needed.
     */
    class PreparedUseCase(
        /** The prepared CameraX [UseCase], bound to the Camera by the [CameraSession]. */
        val useCase: UseCase,
        private val onAttached: () -> Unit,
    ) {
        /** Notify the output that this [UseCase] has been attached to the Camera. */
        fun notifyAttached() = onAttached()
    }

    /** The currently selected [MirrorMode]. */
    val mirrorMode: MirrorMode

    /**
     * Creates a [PreparedUseCase] for the given [config]. This may be called multiple times to probe feature
     * compatibility (e.g. in [ConstraintResolver]); only the winning one gets [PreparedUseCase.notifyAttached].
     * An output may re-use a previously created [UseCase] if the given [config] is the same for it.
     */
    fun createUseCase(mirrorMode: MirrorMode, config: Config): PreparedUseCase
}
