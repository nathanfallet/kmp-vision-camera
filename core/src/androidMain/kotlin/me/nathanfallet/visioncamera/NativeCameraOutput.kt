package me.nathanfallet.visioncamera

import android.util.Range
import androidx.camera.core.UseCase

/**
 * A [CameraOutput] backed by a CameraX [UseCase].
 */
internal interface NativeCameraOutput : CameraOutput {
    /**
     * Resolved configuration for a camera output. `null` values mean "no constraint specified, platform decides".
     */
    data class Config(
        val fpsRange: Range<Int>?,
        val previewStabilizationMode: TargetStabilizationMode?,
        val videoStabilizationMode: TargetStabilizationMode?,
        val videoDynamicRange: TargetDynamicRange?,
        val photoHDR: Boolean?,
    )

    /**
     * A [UseCase] prepared by a [NativeCameraOutput], but not yet attached to the Camera. After it has been bound
     * with `bindToLifecycle(...)`, call [notifyAttached] to let the output know it can use the live [UseCase].
     */
    class PreparedUseCase(
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
     */
    fun createUseCase(mirrorMode: MirrorMode, config: Config): PreparedUseCase
}
