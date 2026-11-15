package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.nathanfallet.visioncamera.CameraController
import me.nathanfallet.visioncamera.CameraDevice
import me.nathanfallet.visioncamera.FPSConstraint
import me.nathanfallet.visioncamera.Recorder
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.TorchMode
import me.nathanfallet.visioncamera.compose.NativePreviewView
import me.nathanfallet.visioncamera.compose.rememberCamera
import me.nathanfallet.visioncamera.compose.rememberCameraDevice
import me.nathanfallet.visioncamera.compose.rememberCameraDevices
import me.nathanfallet.visioncamera.compose.rememberGestureControllers
import me.nathanfallet.visioncamera.compose.rememberPhotoOutput
import me.nathanfallet.visioncamera.compose.rememberPreviewViewRef
import me.nathanfallet.visioncamera.compose.rememberPreviewOutput
import me.nathanfallet.visioncamera.compose.rememberVideoOutput
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * The camera screen: a full-screen preview with tap-to-focus, and controls to take photos, record videos, switch
 * devices, zoom, toggle the torch and pause the session.
 */
@Composable
fun CameraScreen() {
    var position by remember { mutableStateOf(TargetCameraPosition.BACK) }
    var selectedDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var isActive by remember { mutableStateOf(true) }
    var torchMode by remember { mutableStateOf(TorchMode.OFF) }
    var zoom by remember { mutableStateOf<Double?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var lastVideo by remember { mutableStateOf<String?>(null) }
    var enableFrameProcessor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val defaultDevice = rememberCameraDevice(position)
    val device = selectedDevice ?: defaultDevice
    val previewOutput = rememberPreviewOutput()
    val photoOutput = rememberPhotoOutput()
    val videoOutput = rememberVideoOutput()
    val (frameOutput, frameStats) = rememberFrameStats()
    val (objectOutput, scannedCodes) = rememberCodeScanner()
    val previewRef = rememberPreviewViewRef()
    val controller = rememberCamera(
        isActive = isActive,
        device = device ?: position,
        // The frame processor replaces the video output, as not all devices can stream 4 outputs at once.
        outputs = listOfNotNull(
            previewOutput,
            photoOutput,
            if (enableFrameProcessor) frameOutput else videoOutput,
            objectOutput,
        ),
        constraints = listOf(FPSConstraint(30.0)),
        zoom = zoom,
        torchMode = torchMode,
        onStarted = {
            isRunning = true
            lastError = null
        },
        onStopped = { isRunning = false },
        onError = { lastError = it.message ?: it.toString() },
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val gestureControllers = rememberGestureControllers(
            controller = controller,
            enableNativeTapToFocusGesture = true,
            enableNativeZoomGesture = false, // The zoom is controlled by the slider below.
        )
        NativePreviewView(
            previewOutput = previewOutput,
            modifier = Modifier.fillMaxSize(),
            gestureControllers = gestureControllers,
            ref = previewRef,
        )
        ScannedCodesOverlay(codes = scannedCodes, previewRef = previewRef, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusPanel(
                device = device,
                controller = controller,
                isRunning = isRunning,
                isRecording = recorder != null,
                lastVideo = lastVideo,
                frameStats = frameStats.takeIf { enableFrameProcessor },
                scannedCodes = scannedCodes.mapNotNull { it.value },
                error = lastError,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDevices) {
                    DeviceList(
                        selected = device,
                        onSelect = {
                            selectedDevice = it
                            zoom = null
                            showDevices = false
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                attempt(onError = { lastError = it }) {
                                    thumbnail = photoOutput.capturePhoto().use { it.toThumbnail() }
                                }
                            }
                        },
                        enabled = controller != null,
                    ) { Text("Photo") }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                attempt(onError = { lastError = it }) {
                                    val current = recorder
                                    if (current != null) {
                                        current.stopRecording()
                                    } else {
                                        val newRecorder = videoOutput.createRecorder()
                                        newRecorder.startRecording(
                                            onRecordingFinished = { filePath, _ ->
                                                lastVideo = filePath
                                                recorder = null
                                            },
                                            onRecordingError = {
                                                lastError = it.message ?: it.toString()
                                                recorder = null
                                            },
                                        )
                                        recorder = newRecorder
                                    }
                                }
                            }
                        },
                        enabled = controller != null && !enableFrameProcessor,
                    ) { Text(if (recorder != null) "Stop" else "Record") }
                    FilledTonalButton(
                        onClick = { enableFrameProcessor = !enableFrameProcessor },
                        enabled = recorder == null,
                    ) { Text(if (enableFrameProcessor) "Frames off" else "Frames") }
                    thumbnail?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "Last photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
                if (controller != null) {
                    ZoomSlider(controller = controller, zoom = zoom ?: controller.zoom, onZoomChange = { zoom = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        position = if (position == TargetCameraPosition.BACK) {
                            TargetCameraPosition.FRONT
                        } else {
                            TargetCameraPosition.BACK
                        }
                        selectedDevice = null
                        zoom = null
                    }) { Text("Flip") }
                    FilledTonalButton(
                        onClick = { torchMode = if (torchMode == TorchMode.ON) TorchMode.OFF else TorchMode.ON },
                        enabled = device?.hasTorch == true,
                    ) { Text(if (torchMode == TorchMode.ON) "Torch off" else "Torch on") }
                    FilledTonalButton(onClick = { isActive = !isActive }) { Text(if (isActive) "Pause" else "Resume") }
                    FilledTonalButton(onClick = { showDevices = !showDevices }) { Text("Devices") }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun StatusPanel(
    device: CameraDevice?,
    controller: CameraController?,
    isRunning: Boolean,
    isRecording: Boolean,
    lastVideo: String?,
    frameStats: String?,
    scannedCodes: List<String>,
    error: String?,
) {
    Column(
        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Text(device?.localizedName ?: "No device", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(if (isRunning) "Running" else "Stopped", color = if (isRunning) Color.Green else Color.LightGray)
        if (device != null) {
            Text("Type: ${device.type}, ${device.physicalDevices.size} physical device(s)", color = Color.White)
            val maxFps = device.supportedFPSRanges.maxOfOrNull { it.max }?.roundToInt()
            Text("Max FPS: ${maxFps ?: "?"}, Photo HDR: ${device.supportsPhotoHDR}", color = Color.White)
        }
        if (controller != null) {
            Text("Zoom: ${controller.displayableZoomFactor.format()}x", color = Color.White)
        }
        if (isRecording) Text("● Recording", color = Color.Red)
        if (lastVideo != null) Text("Last video: ${lastVideo.substringAfterLast('/')}", color = Color.White)
        if (frameStats != null) Text("Frames: $frameStats", color = Color.White)
        scannedCodes.forEach { Text("Code: $it", color = Color.Green) }
        if (error != null) Text("Error: $error", color = Color.Red)
    }
}

@Composable
private fun ZoomSlider(controller: CameraController, zoom: Double, onZoomChange: (Double) -> Unit) {
    val range = controller.minZoom.toFloat()..controller.maxZoom.toFloat().coerceAtMost(MAX_SLIDER_ZOOM)
    if (range.isEmpty() || range.start == range.endInclusive) return
    Slider(
        value = zoom.toFloat().coerceIn(range),
        onValueChange = { onZoomChange(it.toDouble()) },
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeviceList(selected: CameraDevice?, onSelect: (CameraDevice) -> Unit) {
    val devices = rememberCameraDevices()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium),
    ) {
        items(devices, key = { it.id }) { device ->
            TextButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val marker = if (device.id == selected?.id) "● " else ""
                    Text("$marker${device.localizedName} (${device.position}, ${device.type})", color = Color.White)
                }
            }
        }
    }
}

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

private const val MAX_SLIDER_ZOOM = 10f

private fun Double.format(): String = ((this * 10).roundToInt() / 10.0).toString()
