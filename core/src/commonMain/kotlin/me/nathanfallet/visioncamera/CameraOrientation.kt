package me.nathanfallet.visioncamera

/**
 * Represents the orientation, relative to a base anchor.
 */
enum class CameraOrientation {
    /** The default orientation relative to your base anchor, aka no rotation at all. */
    UP,

    /** Rotated 90° right. Whatever was "top" before is now "right". */
    RIGHT,

    /** Inverted upside down relative to your base anchor. */
    DOWN,

    /** Rotated 90° left. Whatever was "top" before is now "left". */
    LEFT,
}
