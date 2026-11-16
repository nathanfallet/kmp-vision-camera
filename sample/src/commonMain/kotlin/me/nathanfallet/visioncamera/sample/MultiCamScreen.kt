package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.CameraOutputConfiguration
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.CameraSession
import me.nathanfallet.visioncamera.CameraSessionConnection
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.VisionCamera
import me.nathanfallet.visioncamera.compose.NativePreviewView
import me.nathanfallet.visioncamera.compose.rememberCameraDevice
import me.nathanfallet.visioncamera.compose.rememberPreviewOutput

/**
 * The multi-cam screen: the back and front Cameras stream at the same time into a multi-cam session, with one
 * preview each (stacked). Shows a message when the device doesn't support multi-cam sessions.
 */
@Composable
fun MultiCamScreen(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (VisionCamera.supportsMultiCamSessions) {
            MultiCamContent(onClose)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Multi-cam not supported on this device", color = Color.White)
                FilledTonalButton(onClick = onClose) { Text("Back") }
            }
        }
    }
}

@Composable
private fun MultiCamContent(onClose: () -> Unit) {
    val backDevice = rememberCameraDevice(TargetCameraPosition.BACK)
    val frontDevice = rememberCameraDevice(TargetCameraPosition.FRONT)
    val backPreview = rememberPreviewOutput()
    val frontPreview = rememberPreviewOutput()
    var session by remember { mutableStateOf<CameraSession?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backDevice, frontDevice) {
        if (backDevice == null || frontDevice == null) {
            error = "Multi-cam needs both a back and a front Camera (back: ${backDevice != null}, " +
                "front: ${frontDevice != null})"
            return@LaunchedEffect
        }
        error = null
        attempt(onError = { error = it }) {
            val newSession = VisionCamera.createCameraSession(enableMultiCam = true)
            session = newSession
            newSession.addOnStartedListener { isRunning = true }
            newSession.addOnStoppedListener { isRunning = false }
            newSession.addOnErrorListener { error = it.message ?: it.toString() }
            newSession.configure(
                listOf(connection(backDevice, backPreview), connection(frontDevice, frontPreview)),
            )
            newSession.start()
        }
    }
    // Stop the session when leaving the screen: the composition's own scope is cancelled by then.
    DisposableEffect(session) {
        val current = session
        onDispose {
            if (current != null) disposalScope.launch { attempt(onError = {}) { current.stop() } }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NativePreviewView(previewOutput = backPreview, modifier = Modifier.weight(1f).fillMaxSize())
        NativePreviewView(previewOutput = frontPreview, modifier = Modifier.weight(1f).fillMaxSize())
    }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium).padding(12.dp),
        ) {
            Text("Multi-cam", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(if (isRunning) "Running" else "Stopped", color = if (isRunning) Color.Green else Color.LightGray)
            Text("Back: ${backDevice?.localizedName ?: "none"}", color = Color.White)
            Text("Front: ${frontDevice?.localizedName ?: "none"}", color = Color.White)
            error?.let { Text("Error: $it", color = Color.Red) }
        }
        FilledTonalButton(onClick = onClose) { Text("Back") }
    }
}

private fun connection(device: CameraDevice, preview: CameraPreviewOutput) =
    CameraSessionConnection(input = device, outputs = listOf(CameraOutputConfiguration(preview)))

/** Stopping a session must outlive the composition that started it. */
private val disposalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Runs [block], reporting its failures (but not cancellations) to [onError]. */
@Suppress("TooGenericExceptionCaught")
private suspend fun attempt(onError: (String) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.message ?: e.toString())
    }
}
