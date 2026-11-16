package me.nathanfallet.visioncamera.skia

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class SurfacesCacheTest {
    private class FakeContext(val threadId: Long)

    private class FakeSurface(val context: FakeContext, val width: Int, val height: Int)

    private class FakeBackend(private val threadId: () -> Long) : SurfacesCacheBackend<FakeContext, FakeSurface> {
        val disposedSurfaces = mutableListOf<FakeSurface>()
        val destroyedContexts = mutableListOf<Pair<FakeContext, Boolean>>()
        var onDestroyContext: () -> Unit = {}

        override fun createContext() = FakeContext(threadId())

        override fun createSurface(context: FakeContext, width: Int, height: Int): FakeSurface {
            assertEquals(threadId(), context.threadId, "Surfaces must be created on their context's thread")
            return FakeSurface(context, width, height)
        }

        override fun disposeSurface(context: FakeContext, surface: FakeSurface) {
            assertEquals(threadId(), context.threadId, "Surfaces must be disposed on their context's thread")
            disposedSurfaces += surface
        }

        override fun destroyContext(context: FakeContext, surfaces: List<FakeSurface>, isOwnerThread: Boolean) {
            assertEquals(isOwnerThread, threadId() == context.threadId)
            onDestroyContext()
            destroyedContexts += context to isOwnerThread
            disposedSurfaces += surfaces
        }
    }

    private var threadId = 1L
    private var now = 0L
    private val backend = FakeBackend { threadId }
    private val cache = SurfacesCache(backend, currentThreadId = { threadId }, now = { now })

    private fun getSurface(width: Int, height: Int) = cache.withSurface(width, height) { _, surface -> surface }

    @Test
    fun surfacesAreCachedPerThreadAndSize() {
        val surface = getSurface(1920, 1080)
        assertSame(surface, getSurface(1920, 1080))
        assertNotSame(surface, getSurface(1080, 1920))
        threadId = 2
        val otherThreadSurface = getSurface(1920, 1080)
        assertNotSame(surface, otherThreadSurface)
        assertNotSame(surface.context, otherThreadSurface.context)
        assertEquals(3, cache.size)
    }

    @Test
    fun ownSurfacesAreEvictedAfter15SecondsOfInactivity() {
        val old = getSurface(1920, 1080)
        now = MAXIMUM_SURFACE_CACHE_AGE_MS
        val recent = getSurface(1080, 1920)
        assertEquals(emptyList(), backend.disposedSurfaces, "A surface is kept for exactly 15 seconds")
        now = MAXIMUM_SURFACE_CACHE_AGE_MS + 1
        assertSame(recent, getSurface(1080, 1920))
        assertEquals(listOf(old), backend.disposedSurfaces)
        assertEquals(1, cache.size)
        assertEquals(emptyList(), backend.destroyedContexts, "The thread's context is kept while it renders")
    }

    @Test
    fun usingASurfaceKeepsItAlive() {
        val surface = getSurface(1920, 1080)
        repeat(5) {
            now += MAXIMUM_SURFACE_CACHE_AGE_MS
            assertSame(surface, getSurface(1920, 1080))
        }
        assertEquals(emptyList(), backend.disposedSurfaces)
    }

    @Test
    fun idleThreadsAreDestroyedByOtherThreads() {
        val idleSurface = getSurface(1920, 1080)
        threadId = 2
        getSurface(1920, 1080)
        now = MAXIMUM_SURFACE_CACHE_AGE_MS + 1
        getSurface(1920, 1080)
        assertEquals(listOf(idleSurface.context to false), backend.destroyedContexts)
        assertEquals(listOf(idleSurface), backend.disposedSurfaces)
        assertEquals(1, cache.size)
    }

    @Test
    fun threadsWithARecentSurfaceAreKeptByOtherThreads() {
        getSurface(1920, 1080)
        now = MAXIMUM_SURFACE_CACHE_AGE_MS
        getSurface(1080, 1920)
        threadId = 2
        now = MAXIMUM_SURFACE_CACHE_AGE_MS + 1
        getSurface(1920, 1080)
        assertEquals(emptyList(), backend.destroyedContexts)
        assertEquals(emptyList(), backend.disposedSurfaces, "Other threads never dispose single surfaces")
        assertEquals(3, cache.size)
    }

    @Test
    fun clearDestroysEveryContext() {
        val ownSurface = getSurface(1920, 1080)
        threadId = 2
        val otherSurface = getSurface(1920, 1080)
        threadId = 1
        assertSame(ownSurface.context, cache.withCurrentContext { it })
        cache.clear()
        assertEquals(listOf(ownSurface.context to true, otherSurface.context to false), backend.destroyedContexts)
        assertTrue(backend.disposedSurfaces.containsAll(listOf(ownSurface, otherSurface)))
        assertEquals(0, cache.size)
        assertNull(cache.withCurrentContext { it })
        assertNotSame(ownSurface, getSurface(1920, 1080), "A new context is created after clearing")
    }

    @Test
    fun clearWaitsUntilAnotherThreadIsDoneWithItsCurrentContext() = runTest {
        val surface = getSurface(1920, 1080)
        val events = mutableListOf<String>()
        val eventsLock = SkiaLock()
        backend.onDestroyContext = { eventsLock.withLock { events += "destroyed" } }
        val isUsingContext = CompletableDeferred<Unit>()
        withContext(Dispatchers.Default) {
            // Thread 1 copies a GPU image with its context (like makeNonTextureImage())...
            launch {
                cache.withCurrentContext { context ->
                    assertSame(surface.context, context)
                    isUsingContext.complete(Unit)
                    // Keep using the context for a while, giving clear() every chance to destroy it meanwhile.
                    val start = TimeSource.Monotonic.markNow()
                    while (start.elapsedNow() < 200.milliseconds) {
                        // Busy, without releasing the thread.
                    }
                    eventsLock.withLock { events += "used" }
                }
            }
            // ...while thread 2 clears the cache (like SkiaCamera leaving the composition).
            launch {
                isUsingContext.await()
                threadId = 2
                cache.clear()
            }
        }
        assertEquals(listOf("used", "destroyed"), events, "The context was destroyed while it was in use")
        assertEquals(listOf(surface.context to false), backend.destroyedContexts)
        threadId = 1
        assertNull(cache.withCurrentContext { it }, "A destroyed context is not passed anymore")
    }
}
