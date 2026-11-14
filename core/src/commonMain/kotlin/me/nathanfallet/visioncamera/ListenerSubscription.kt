package me.nathanfallet.visioncamera

/**
 * Represents a subscription to any kind of listener.
 *
 * You can remove the subscription by calling [remove], for example in a `DisposableEffect`'s `onDispose`.
 */
fun interface ListenerSubscription {
    /**
     * Remove the listener subscription.
     */
    fun remove()
}
