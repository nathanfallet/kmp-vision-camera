package me.nathanfallet.visioncamera

/**
 * Listens to [CameraOrientation] changes of the device or the interface.
 *
 * @see CameraFactory.createOrientationManager
 */
interface OrientationManager {
    /** The [OrientationSource] this manager tracks. */
    val source: OrientationSource

    /** The current [CameraOrientation], or `null` if it is not known yet. */
    val currentOrientation: CameraOrientation?

    /** Starts listening to orientation changes, calling [onChanged] with each new orientation. */
    fun startOrientationUpdates(onChanged: (orientation: CameraOrientation) -> Unit)

    /** Stops listening to orientation changes. */
    fun stopOrientationUpdates()
}
