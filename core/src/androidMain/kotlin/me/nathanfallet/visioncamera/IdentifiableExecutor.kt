package me.nathanfallet.visioncamera

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * A serial [Executor] running on a single named thread, which can tell whether the caller is running on it.
 */
internal class IdentifiableExecutor(
    private val name: String,
) : Executor {
    private val threadRef = AtomicReference<Thread?>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        // Daemon thread, so an unused executor never keeps the process alive.
        Thread(runnable, name).also {
            it.isDaemon = true
            threadRef.set(it)
        }
    }

    override fun execute(command: Runnable) {
        executor.execute(command)
    }

    /**
     * Shuts this executor down: already submitted tasks still run, new ones are rejected, and its thread stops once
     * they are done.
     */
    fun shutdown() {
        executor.shutdown()
    }

    /** Whether the calling thread is this executor's thread. */
    val isRunningOnExecutor: Boolean
        get() = Thread.currentThread() == threadRef.get()

    override fun toString(): String = name
}
