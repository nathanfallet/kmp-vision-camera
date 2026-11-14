package me.nathanfallet.visioncamera

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A thread-safe list of listeners, returning a [ListenerSubscription] for each added listener.
 */
internal class ListenerList<T> {
    private val listeners = CopyOnWriteArrayList<T>()

    fun add(listener: T): ListenerSubscription {
        listeners.add(listener)
        return ListenerSubscription { listeners.remove(listener) }
    }

    fun forEach(action: (T) -> Unit) = listeners.forEach(action)
}
