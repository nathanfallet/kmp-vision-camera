package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android [CameraSession], binding CameraX use-cases to a [CameraSessionLifecycle].
 */
@Suppress("TooManyFunctions")
internal class AndroidCameraSession(
    private val cameraProvider: ProcessCameraProvider,
    private val enableMultiCam: Boolean,
) : CameraSession, ActiveCameraSession.LifecycleListener {
    private var lifecycleOwner: CameraSessionLifecycle? = null
    private var activeSession: ActiveCameraSession? = null

    private val onStartedListeners = ListenerList<() -> Unit>()
    private val onStoppedListeners = ListenerList<() -> Unit>()
    private val onErrorListeners = ListenerList<(Throwable) -> Unit>()
    private val onInterruptionStartedListeners = ListenerList<(InterruptionReason) -> Unit>()
    private val onInterruptionEndedListeners = ListenerList<() -> Unit>()

    override val isRunning: Boolean get() = activeSession?.isRunning ?: false

    @MainThread
    private fun requireLifecycleOwner(): CameraSessionLifecycle =
        lifecycleOwner ?: CameraSessionLifecycle().also { lifecycleOwner = it }

    @SuppressLint("RestrictedApi")
    override suspend fun configure(
        connections: List<CameraSessionConnection>,
        config: CameraSessionConfiguration,
    ): List<CameraController> = withContext(Dispatchers.Main) {
        Log.i(TAG, "Reconfiguring CameraSession with ${connections.size} connection(s)...")
        if (AndroidContext.context.getPermissionStatus(android.Manifest.permission.CAMERA) !=
            PermissionStatus.AUTHORIZED
        ) {
            throw VisionCameraException("Camera permission has not been granted!")
        }
        if (connections.size > 1 && !enableMultiCam) {
            throw VisionCameraException(
                "Multiple connections were given, but this CameraSession was not created as a multi-cam session!",
            )
        }

        // 1. Unbind all inputs/outputs
        cameraProvider.unbindAll()
        activeSession?.close()
        activeSession = null

        // 2. Build up Camera depending on connections count
        when (connections.size) {
            0 -> emptyList()
            1 -> listOf(configureSingle(connections.single()))
            else -> configureMulti(connections)
        }
    }

    @MainThread
    private fun configureSingle(connection: CameraSessionConnection): CameraController {
        val cameraInfo = cameraProvider.getCameraInfo(connection.input)
        val resolved = ConstraintResolver.resolveConstraints(cameraInfo, connection.outputs, connection.constraints)
        Log.i(TAG, "Binding use-cases: ${resolved.sessionConfig.useCases}")
        // Notify the callback that we resolved the constraints to a specific config
        connection.onSessionConfigSelected?.invoke(resolved.toCameraSessionConfig())

        val camera = cameraProvider.bindToLifecycle(
            requireLifecycleOwner(),
            cameraInfo.cameraSelector,
            resolved.sessionConfig,
        )
        // Notify outputs that their use-cases are now attached to the Camera
        resolved.preparedUseCases.forEach { it.notifyAttached() }
        activeSession = ActiveCameraSessionSingle(camera, this)
        applyInitialConfig(camera, connection.initialZoom, connection.initialExposureBias)
        return AndroidCameraController(camera)
    }

    /**
     * In Multi-Cam we cannot use the CameraX `SessionConfig` API, so constraints are not supported (like in
     * VisionCamera, see https://issuetracker.google.com/issues/470629644).
     */
    @MainThread
    private fun configureMulti(connections: List<CameraSessionConnection>): List<CameraController> {
        val allPreparedUseCases = mutableListOf<NativeCameraOutput.PreparedUseCase>()
        val outputConfig = emptyList<Constraint>().toConfig()
        val configs = connections.map { connection ->
            val cameraInfo = cameraProvider.getCameraInfo(connection.input)
            val preparedUseCases = connection.outputs.map { outputConfiguration ->
                val output = outputConfiguration.output as? NativeCameraOutput
                    ?: throw VisionCameraException(
                        "Output ${outputConfiguration.output} was not created by VisionCamera!",
                    )
                output.createUseCase(outputConfiguration.mirrorMode, outputConfig)
            }
            allPreparedUseCases.addAll(preparedUseCases)
            val useCaseGroup = UseCaseGroup.Builder()
            preparedUseCases.forEach { useCaseGroup.addUseCase(it.useCase) }
            ConcurrentCamera.SingleCameraConfig(
                cameraInfo.cameraSelector,
                useCaseGroup.build(),
                requireLifecycleOwner(),
            )
        }
        val concurrentCamera = cameraProvider.bindToLifecycle(configs)
        allPreparedUseCases.forEach { it.notifyAttached() }
        activeSession = ActiveCameraSessionMulti(concurrentCamera, this)
        return concurrentCamera.cameras.mapIndexed { i, camera ->
            val connection = connections[i]
            applyInitialConfig(camera, connection.initialZoom, connection.initialExposureBias)
            AndroidCameraController(camera)
        }
    }

    @MainThread
    private fun applyInitialConfig(camera: Camera, initialZoom: Double?, initialExposureBias: Double?) {
        initialZoom?.let { camera.cameraControl.setZoomRatio(it.toFloat()) }
        initialExposureBias?.let { camera.cameraControl.setExposureCompensationIndex(it.toInt()) }
    }

    override suspend fun start() = withContext(Dispatchers.Main) {
        requireLifecycleOwner().setActive(true)
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        requireLifecycleOwner().setActive(false)
    }

    // Lifecycle Changed Callbacks

    override fun onStarted() = onStartedListeners.forEach { it() }

    override fun onStopped() = onStoppedListeners.forEach { it() }

    override fun onError(error: Throwable) = onErrorListeners.forEach { it(error) }

    override fun onInterruptionStarted() = onInterruptionStartedListeners.forEach { it(InterruptionReason.UNKNOWN) }

    override fun onInterruptionEnded() = onInterruptionEndedListeners.forEach { it() }

    // Adding Listeners

    override fun addOnStartedListener(onStarted: () -> Unit): ListenerSubscription = onStartedListeners.add(onStarted)

    override fun addOnStoppedListener(onStopped: () -> Unit): ListenerSubscription = onStoppedListeners.add(onStopped)

    override fun addOnErrorListener(onError: (error: Throwable) -> Unit): ListenerSubscription =
        onErrorListeners.add(onError)

    override fun addOnInterruptionStartedListener(
        onInterruptionStarted: (reason: InterruptionReason) -> Unit,
    ): ListenerSubscription = onInterruptionStartedListeners.add(onInterruptionStarted)

    override fun addOnInterruptionEndedListener(onInterruptionEnded: () -> Unit): ListenerSubscription =
        onInterruptionEndedListeners.add(onInterruptionEnded)
}
