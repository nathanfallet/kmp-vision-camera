package me.nathanfallet.visioncamera

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.camera.core.CameraIdentifier
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraPresenceListener
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.edit
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors

/**
 * The Android [CameraDeviceFactory], backed by a CameraX [ProcessCameraProvider].
 */
internal class AndroidCameraDeviceFactory(
    private val cameraProvider: ProcessCameraProvider,
) : CameraDeviceFactory {
    private val executor = Executors.newSingleThreadExecutor()
    private val context: Context get() = AndroidContext.context
    private val sharedPreferences: SharedPreferences
        get() = context.getSharedPreferences("me.nathanfallet.visioncamera", Context.MODE_PRIVATE)

    override val cameraDevices: List<CameraDevice>
        get() = cameraProvider.availableCameraInfos.map { AndroidCameraDevice(it) }

    override var userPreferredCamera: CameraDevice?
        get() = sharedPreferences.getString(PREFERRED_CAMERA_KEY, null)?.let { getCameraForId(it) }
        set(value) {
            sharedPreferences.edit {
                if (value != null) putString(PREFERRED_CAMERA_KEY, value.id) else remove(PREFERRED_CAMERA_KEY)
            }
        }

    override val supportedMultiCamDeviceCombinations: List<List<CameraDevice>>
        get() = cameraProvider.availableConcurrentCameraInfos.map { infos -> infos.map { AndroidCameraDevice(it) } }

    override fun addOnCameraDevicesChangedListener(
        listener: (newDevices: List<CameraDevice>) -> Unit,
    ): ListenerSubscription {
        val presenceListener = object : CameraPresenceListener {
            override fun onCamerasAdded(cameraIdentifiers: Set<CameraIdentifier>) {
                if (cameraIdentifiers.isNotEmpty()) listener(cameraDevices)
            }

            override fun onCamerasRemoved(cameraIdentifiers: Set<CameraIdentifier>) {
                if (cameraIdentifiers.isNotEmpty()) listener(cameraDevices)
            }
        }
        cameraProvider.addCameraPresenceListener(executor, presenceListener)
        return ListenerSubscription { cameraProvider.removeCameraPresenceListener(presenceListener) }
    }

    override fun getCameraForId(id: String): CameraDevice? =
        cameraProvider.availableCameraInfos.firstOrNull { it.cameraIdOrNull == id }?.let { AndroidCameraDevice(it) }

    override suspend fun getSupportedExtensions(camera: CameraDevice): List<CameraExtension> {
        val cameraInfo = (camera as? NativeCameraDevice)?.cameraInfo
            ?: throw VisionCameraException("Camera $camera was not created by VisionCamera!")
        val extensionsManager = ExtensionsManager.getInstanceAsync(context, cameraProvider).await()
        return ALL_EXTENSIONS
            .filter { extensionsManager.isExtensionAvailable(cameraInfo.cameraSelector, it) }
            .map { AndroidCameraExtension(cameraInfo, extensionsManager, it) }
    }

    override fun getDefaultCamera(position: TargetCameraPosition): CameraDevice? = try {
        AndroidCameraDevice(cameraProvider.getDefaultCamera(position))
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "No default ${position.name} Camera found!", e)
        null
    }

    private companion object {
        const val PREFERRED_CAMERA_KEY = "preferred_camera_id"
        val ALL_EXTENSIONS = listOf(
            ExtensionMode.HDR,
            ExtensionMode.BOKEH,
            ExtensionMode.FACE_RETOUCH,
            ExtensionMode.NIGHT,
            ExtensionMode.AUTO,
        )
    }
}

/**
 * A CameraX vendor extension.
 */
internal class AndroidCameraExtension(
    private val cameraInfo: CameraInfo,
    private val extensionsManager: ExtensionsManager,
    /** The CameraX [ExtensionMode]. */
    val extensionMode: Int,
) : CameraExtension {
    override val type: CameraExtensionType
        get() = when (extensionMode) {
            ExtensionMode.BOKEH -> CameraExtensionType.BOKEH
            ExtensionMode.HDR -> CameraExtensionType.HDR
            ExtensionMode.NIGHT -> CameraExtensionType.NIGHT
            ExtensionMode.FACE_RETOUCH -> CameraExtensionType.FACE_RETOUCH
            else -> CameraExtensionType.AUTO
        }

    override val supportsFrameStreaming: Boolean
        get() = extensionsManager.isImageAnalysisSupported(cameraInfo.cameraSelector, extensionMode)
}
