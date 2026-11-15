package me.nathanfallet.visioncamera

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * A native thread: an `Executor` on Android, a `DispatchQueue` on iOS.
 *
 * Streaming outputs like the [CameraFrameOutput] expose the [NativeThread] their callbacks run on, so further work can
 * run on the same thread without any thread hop. A [NativeThread] may use a thread pool, but it never runs code in
 * parallel: execution is always serial.
 *
 * @see NativeThreadFactory
 */
interface NativeThread {
    /** This thread's ID. */
    val id: String

    /** Runs the given [task] on this thread. */
    fun runOnThread(task: () -> Unit)
}

/**
 * A [CoroutineDispatcher] running coroutines on this [NativeThread], e.g. to process frames asynchronously on the
 * output's thread with `withContext(frameOutput.thread.asCoroutineDispatcher())`.
 */
fun NativeThread.asCoroutineDispatcher(): CoroutineDispatcher = NativeThreadDispatcher(this)

private class NativeThreadDispatcher(private val thread: NativeThread) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = thread.runOnThread { block.run() }

    override fun toString(): String = "NativeThread(${thread.id})"
}

/**
 * Creates [NativeThread]s, for advanced asynchronous processing. Camera outputs already create their own threads, so
 * this is rarely needed.
 *
 * ```kotlin
 * val thread = NativeThreadFactory.createNativeThread("async-processor")
 * ```
 */
object NativeThreadFactory {
    /** Creates a new [NativeThread] with the given [name]. */
    fun createNativeThread(name: String): NativeThread = createPlatformNativeThread(name)
}

internal expect fun createPlatformNativeThread(name: String): NativeThread
