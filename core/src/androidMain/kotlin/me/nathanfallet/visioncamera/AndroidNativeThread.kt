package me.nathanfallet.visioncamera

import java.util.concurrent.Executor

/**
 * The Android [NativeThread], backed by an [Executor].
 */
internal class AndroidNativeThread(
    /** The [Executor] this thread runs tasks on. */
    val executor: Executor,
) : NativeThread {
    override val id: String
        get() = executor.toString()

    override fun runOnThread(task: () -> Unit) {
        executor.execute(task)
    }
}
