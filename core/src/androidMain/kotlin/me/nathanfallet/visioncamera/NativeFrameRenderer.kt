package me.nathanfallet.visioncamera

import android.view.Surface

/**
 * A [FrameRenderer] created by VisionCamera on Android, rendering into a [Surface].
 *
 * @see FrameRendererView
 */
interface NativeFrameRenderer {
    /** Connects the given [surface], which the next rendered Frames are written to. */
    fun connectSurface(surface: Surface)

    /** Disconnects the currently connected [Surface], if any. */
    fun disconnectSurface()
}
