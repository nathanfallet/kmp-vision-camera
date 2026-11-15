package me.nathanfallet.visioncamera

import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_t

/**
 * The iOS [NativeThread], running tasks on a serial [dispatch_queue_t] (port of V5's `HybridNativeThread.swift`).
 */
internal class IosNativeThread(
    /** The serial queue tasks run on. */
    val queue: dispatch_queue_t,
    /** The queue's label (V5's `queue.label`). */
    private val label: String,
) : NativeThread {
    override val id: String get() = label

    override fun runOnThread(task: () -> Unit) {
        dispatch_async(queue, task)
    }

    override fun toString(): String = "NativeThread($label)"
}
