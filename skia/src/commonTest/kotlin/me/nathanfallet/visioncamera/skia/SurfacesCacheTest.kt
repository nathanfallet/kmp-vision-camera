package me.nathanfallet.visioncamera.skia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SurfacesCacheTest {
    private class FakeContext(val threadId: Long)

    private class FakeSurface(val context: FakeContext, val width: Int, val height: Int)

    private class FakeBackend(private val threadId: () -> Long) : SurfacesCacheBackend<FakeContext, FakeSurface> {
        val disposedSurfaces = mutableListOf<FakeSurface>()
        val destroyedContexts = mutableListOf<Pair<FakeContext, Boolean>>()

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
        assertSame(ownSurface.context, cache.currentContext())
        cache.clear()
        assertEquals(listOf(ownSurface.context to true, otherSurface.context to false), backend.destroyedContexts)
        assertTrue(backend.disposedSurfaces.containsAll(listOf(ownSurface, otherSurface)))
        assertEquals(0, cache.size)
        assertNull(cache.currentContext())
        assertNotSame(ownSurface, getSurface(1920, 1080), "A new context is created after clearing")
    }
}
