package me.nathanfallet.visioncamera

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * A [LifecycleOwner] that follows the app's lifecycle ([ProcessLifecycleOwner]), with an additional [isActive]
 * switch that pauses or resumes the camera whatever the app's lifecycle is.
 *
 * CameraX opens the camera when this lifecycle is started, and closes it when it is stopped.
 */
@MainThread
internal class CameraSessionLifecycle : LifecycleOwner, DefaultLifecycleObserver {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val processLifecycle: Lifecycle get() = ProcessLifecycleOwner.get().lifecycle
    private var isActive = false
    private var isDestroyed = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    init {
        processLifecycle.addObserver(this)
        updateLifecycle()
    }

    fun setActive(active: Boolean) {
        isActive = active
        updateLifecycle()
    }

    fun destroy() {
        isDestroyed = true
        processLifecycle.removeObserver(this)
        moveTo(Lifecycle.State.DESTROYED)
    }

    override fun onStart(owner: LifecycleOwner) = updateLifecycle()

    override fun onResume(owner: LifecycleOwner) = updateLifecycle()

    override fun onPause(owner: LifecycleOwner) = updateLifecycle()

    override fun onStop(owner: LifecycleOwner) = updateLifecycle()

    private fun updateLifecycle() {
        if (isDestroyed) return
        val processState = processLifecycle.currentState
        val newState = when {
            !isActive -> Lifecycle.State.CREATED
            processState.isAtLeast(Lifecycle.State.RESUMED) -> Lifecycle.State.RESUMED
            processState.isAtLeast(Lifecycle.State.STARTED) -> Lifecycle.State.STARTED
            else -> Lifecycle.State.CREATED
        }
        moveTo(newState)
    }

    private fun moveTo(state: Lifecycle.State) {
        val current = lifecycleRegistry.currentState
        if (current == state) return
        Log.i(TAG, "Switching camera lifecycle state from $current to $state...")
        // LifecycleRegistry moves monotonically through all intermediate events.
        lifecycleRegistry.currentState = state
    }
}
