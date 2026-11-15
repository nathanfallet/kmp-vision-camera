package me.nathanfallet.visioncamera.barcode.compose

import androidx.compose.runtime.RememberObserver

/**
 * Holds a remembered [value], and calls [dispose] on it once it leaves the composition (or is recreated with new
 * keys), or if the composition that created it is abandoned.
 */
internal class RememberedDisposable<T>(
    val value: T,
    private val dispose: (T) -> Unit,
) : RememberObserver {
    override fun onRemembered() = Unit

    override fun onForgotten() {
        dispose(value)
    }

    override fun onAbandoned() {
        dispose(value)
    }
}
