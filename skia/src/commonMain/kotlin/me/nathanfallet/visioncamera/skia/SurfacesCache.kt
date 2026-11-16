package me.nathanfallet.visioncamera.skia

import kotlin.time.TimeSource

/** If a Surface hasn't been used in 15 000 ms, it will be deleted from the cache. */
internal const val MAXIMUM_SURFACE_CACHE_AGE_MS = 15_000L

/**
 * Creates and disposes the GPU contexts ([C], one per thread) and offscreen surfaces ([S]) of a [SurfacesCache].
 */
internal interface SurfacesCacheBackend<C : Any, S : Any> {
    /** Creates a GPU context for the current thread. */
    fun createContext(): C

    /** Creates an offscreen surface of the given size with the [context] of the current thread. */
    fun createSurface(context: C, width: Int, height: Int): S

    /** Disposes the [surface], on the thread of its [context]. */
    fun disposeSurface(context: C, surface: S)

    /**
     * Disposes the [context] and its remaining [surfaces], on the thread of the [context] if [isOwnerThread] is `true`,
     * or on any other thread (while the context's thread is not using it) otherwise.
     */
    fun destroyContext(context: C, surfaces: List<S>, isOwnerThread: Boolean)
}

/**
 * A cache of offscreen surfaces per thread and size (V5's SurfacesCache.ts).
 *
 * Graphic APIs typically use thread-local state, so keeping one GPU context (and its surfaces) per thread is a safe
 * way to make sure we don't violate threading concerns. Unused surfaces are evicted after [maximumAgeMs] of
 * inactivity: by their own thread when it renders again, or, when all the surfaces of a thread are unused, by any
 * other thread (destroying the whole context of the idle thread).
 */
internal class SurfacesCache<C : Any, S : Any>(
    private val backend: SurfacesCacheBackend<C, S>,
    private val currentThreadId: () -> Long,
    private val now: () -> Long,
    private val maximumAgeMs: Long = MAXIMUM_SURFACE_CACHE_AGE_MS,
) {
    private class CachedSurface<S>(val width: Int, val height: Int, val surface: S, var lastUsedTimestamp: Long)

    private class ThreadCache<C, S>(val threadId: Long, val context: C) {
        /** Held while the context is in use, so that other threads never destroy it at the same time. */
        val lock = SkiaLock()
        val surfaces = mutableListOf<CachedSurface<S>>()
        var isDestroyed = false
    }

    private val lock = SkiaLock()
    private val threads = mutableListOf<ThreadCache<C, S>>()

    /** The number of cached surfaces, of all threads. */
    val size: Int get() = lock.withLock { threads.sumOf { it.surfaces.size } }

    /**
     * Gets (or lazily creates) a surface of the given [width] and [height] for the current thread, and calls [block]
     * with it and the thread's GPU context, while no other thread can destroy them.
     */
    fun <T> withSurface(width: Int, height: Int, block: (context: C, surface: S) -> T): T {
        while (true) {
            val threadId = currentThreadId()
            val (threadCache, surface) = getSurface(threadId, width, height)
            val result = threadCache.lock.withLock {
                // Another thread may have cleared the cache in the meantime: start over with a new context.
                if (threadCache.isDestroyed) null else Result(block(threadCache.context, surface))
            }
            if (result != null) return result.value
        }
    }

    /**
     * Calls [block] with the GPU context of the current thread (or `null` if it has none, or if it was destroyed),
     * while no other thread can destroy it.
     */
    fun <T> withCurrentContext(block: (context: C?) -> T): T {
        val threadId = currentThreadId()
        val threadCache = lock.withLock { threads.firstOrNull { it.threadId == threadId } } ?: return block(null)
        return threadCache.lock.withLock { block(threadCache.context.takeUnless { threadCache.isDestroyed }) }
    }

    /** Disposes every cached surface and GPU context. */
    fun clear() {
        val removed = lock.withLock { threads.toList().also { threads.clear() } }
        val threadId = currentThreadId()
        removed.forEach { destroy(it, isOwnerThread = it.threadId == threadId) }
    }

    private fun getSurface(threadId: Long, width: Int, height: Int): Pair<ThreadCache<C, S>, S> {
        val timestamp = now()
        val staleThreads = mutableListOf<ThreadCache<C, S>>()
        val staleSurfaces = mutableListOf<S>()
        val result = lock.withLock {
            val threadCache = threads.firstOrNull { it.threadId == threadId }
                ?: ThreadCache<C, S>(threadId, backend.createContext()).also { threads += it }
            val cachedSurface = threadCache.surfaces.firstOrNull { it.width == width && it.height == height }
            val surface = if (cachedSurface != null) {
                // We found a Surface in our cache! Before we return it, set its last used timestamp to now.
                cachedSurface.lastUsedTimestamp = timestamp
                cachedSurface.surface
            } else {
                // We don't have a cached Surface - create one...
                backend.createSurface(threadCache.context, width, height).also {
                    threadCache.surfaces += CachedSurface(width, height, it, timestamp)
                }
            }
            // Remove all cached Surfaces that haven't been used in a while: our own ones now, and the ones of other
            // threads once none of them is used anymore (their whole context then).
            threads.toList().forEach { cache ->
                val isStale = { cached: CachedSurface<S> -> timestamp - cached.lastUsedTimestamp > maximumAgeMs }
                if (cache === threadCache) {
                    val stale = cache.surfaces.filter(isStale)
                    cache.surfaces.removeAll(stale)
                    staleSurfaces += stale.map { it.surface }
                } else if (cache.surfaces.all(isStale)) {
                    threads.remove(cache)
                    staleThreads += cache
                }
            }
            threadCache to surface
        }
        // Dispose outside of the cache's lock, so that a thread rendering with its context never blocks the others.
        if (staleSurfaces.isNotEmpty()) {
            result.first.lock.withLock { staleSurfaces.forEach { backend.disposeSurface(result.first.context, it) } }
        }
        staleThreads.forEach { destroy(it, isOwnerThread = false) }
        return result
    }

    private fun destroy(threadCache: ThreadCache<C, S>, isOwnerThread: Boolean) = threadCache.lock.withLock {
        if (threadCache.isDestroyed) return@withLock
        threadCache.isDestroyed = true
        backend.destroyContext(threadCache.context, threadCache.surfaces.map { it.surface }, isOwnerThread)
        threadCache.surfaces.clear()
    }

    private class Result<T>(val value: T)
}

/** A lock that the thread holding it can acquire again. */
internal expect class SkiaLock() {
    /** Runs [block] while holding this lock. */
    fun <T> withLock(block: () -> T): T
}

/** A unique identifier of the current thread. */
internal expect fun currentThreadId(): Long

private val startMark = TimeSource.Monotonic.markNow()

/** The current monotonic time, in milliseconds. */
internal fun monotonicNowMs(): Long = startMark.elapsedNow().inWholeMilliseconds
