package me.nathanfallet.visioncamera

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ConcurrentCamera
import androidx.lifecycle.Observer
import java.io.Closeable

/**
 * A bound CameraX session, observing the state of its cameras.
 */
internal interface ActiveCameraSession : Closeable {
    /**
     * Listens to the state of an [ActiveCameraSession].
     */
    interface LifecycleListener {
        fun onStarted()

        fun onStopped()

        fun onError(error: Throwable)

        fun onInterruptionStarted()

        fun onInterruptionEnded()
    }

    val isRunning: Boolean
    val cameras: List<Camera>
}

/**
 * An [ActiveCameraSession] with a single [Camera].
 */
internal class ActiveCameraSessionSingle(
    private val camera: Camera,
    private val listener: ActiveCameraSession.LifecycleListener,
) : ActiveCameraSession {
    override var isRunning: Boolean = camera.cameraInfo.cameraState.value?.type == CameraState.Type.OPEN
        private set
    override val cameras: List<Camera> = listOf(camera)

    private var currentError: CameraState.StateError? = null
    private val observer = Observer<CameraState> { updateCameraState(it) }

    init {
        camera.cameraInfo.cameraState.observeForever(observer)
    }

    override fun close() {
        camera.cameraInfo.cameraState.removeObserver(observer)
    }

    private fun updateCameraState(state: CameraState?) {
        Log.i(TAG, "Camera #${camera.cameraInfo.cameraIdOrNull} state changed: ${state?.type} | ${state?.error}")
        val isNowRunning = state?.type == CameraState.Type.OPEN
        if (isRunning != isNowRunning) {
            isRunning = isNowRunning
            if (isNowRunning) listener.onStarted() else listener.onStopped()
        }

        val error = state?.error
        if (currentError != error) {
            if (error != null) {
                // RECOVERABLE errors are treated as interruptions, as CameraX automatically retries.
                when (error.type) {
                    CameraState.ErrorType.CRITICAL ->
                        listener.onError(VisionCameraException(error.reason, error.cause))
                    CameraState.ErrorType.RECOVERABLE -> listener.onInterruptionStarted()
                }
            } else if (currentError?.type == CameraState.ErrorType.RECOVERABLE) {
                // It was a recoverable error that is now over: the interruption ended.
                listener.onInterruptionEnded()
            }
            currentError = error
        }
    }
}

/**
 * An [ActiveCameraSession] with multiple concurrent cameras. It collapses the started/stopped events of its
 * cameras into single events.
 */
internal class ActiveCameraSessionMulti(
    private val camera: ConcurrentCamera,
    private val listener: ActiveCameraSession.LifecycleListener,
) : ActiveCameraSession, ActiveCameraSession.LifecycleListener {
    private val singleSessions = camera.cameras.map { ActiveCameraSessionSingle(it, this) }
    private var currentIsRunning = isRunning

    override val isRunning: Boolean get() = singleSessions.any { it.isRunning }
    override val cameras: List<Camera> get() = camera.cameras

    override fun close() = singleSessions.forEach { it.close() }

    private fun onIsRunningChanged() {
        val isNowRunning = isRunning
        if (currentIsRunning != isNowRunning) {
            currentIsRunning = isNowRunning
            if (isNowRunning) listener.onStarted() else listener.onStopped()
        }
    }

    override fun onStarted() = onIsRunningChanged()

    override fun onStopped() = onIsRunningChanged()

    override fun onError(error: Throwable) = listener.onError(error)

    override fun onInterruptionStarted() = listener.onInterruptionStarted()

    override fun onInterruptionEnded() = listener.onInterruptionEnded()
}
