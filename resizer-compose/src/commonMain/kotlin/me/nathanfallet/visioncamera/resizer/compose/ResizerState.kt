package me.nathanfallet.visioncamera.resizer.compose

import me.nathanfallet.visioncamera.resizer.Resizer

/**
 * The current state of [rememberResizer]:
 * - [Loading]: The [Resizer] is still being created.
 * - [Ready]: The [Resizer] has been created successfully and is ready to use.
 * - [Error]: Creating the [Resizer] failed. Inspect [error] for details.
 */
sealed interface ResizerState {
    /** The [Resizer], once it is [Ready], or `null`. */
    val resizer: Resizer?

    /** Why creating the [Resizer] failed, if it is an [Error], or `null`. */
    val error: Throwable?

    /** The [Resizer] is still being created. */
    data object Loading : ResizerState {
        override val resizer: Resizer? get() = null
        override val error: Throwable? get() = null
    }

    /** The [Resizer] has been created successfully and is ready to use. */
    data class Ready(override val resizer: Resizer) : ResizerState {
        override val error: Throwable? get() = null
    }

    /** Creating the [Resizer] failed, because of the given [error]. */
    data class Error(override val error: Throwable) : ResizerState {
        override val resizer: Resizer? get() = null
    }
}
