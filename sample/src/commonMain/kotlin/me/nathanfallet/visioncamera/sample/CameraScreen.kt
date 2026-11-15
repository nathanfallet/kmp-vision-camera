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
import androidx.compose.material3.Button
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
import me.nathanfallet.visioncamera.MediaType
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
 * What the session streams besides the preview and photo outputs: not all devices can stream 4 outputs at once, and
 * on Android the frame output and the barcode scanner output are both CameraX `ImageAnalysis` use cases, of which
 * only one can be bound with the preview and photo use cases.
 */
private enum class StreamMode {
    /** The video output, to record videos. */
    VIDEO,

    /** The frame output, measuring the frame processor's FPS. */
    FRAMES,

    /** The barcode scanner output, scanning all barcode formats. */
    CODES,

    /** The depth output, measuring the depth frames' FPS. */
    DEPTH,
}

/**
 * The camera screen: a full-screen preview with tap-to-focus, and controls to take photos, record videos, process
 * frames or depth, scan barcodes, switch devices, zoom, toggle the torch and pause the session.
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
    var streamMode by remember { mutableStateOf(StreamMode.VIDEO) }
    val scope = rememberCoroutineScope()

    val defaultDevice = rememberCameraDevice(position)
    // Only some Cameras capture depth (e.g. the dual or TrueDepth Cameras, not the back wide-angle Camera of an
    // iPhone 11), like in VisionCamera: the depth mode picks one at the same position.
    val depthDevice = rememberCameraDevices().firstOrNull {
        it.position.name == position.name && MediaType.DEPTH in it.mediaTypes
    }
    val device = selectedDevice ?: depthDevice?.takeIf { streamMode == StreamMode.DEPTH } ?: defaultDevice
    val previewOutput = rememberPreviewOutput()
    val photoOutput = rememberPhotoOutput()
    val videoOutput = rememberVideoOutput()
    val (frameOutput, frameStats) = rememberFrameStats()
    val (depthOutput, depthStats) = rememberDepthStats()
    val (objectOutput, scannedCodes) = rememberObjectCodeScanner()
    val barcodeScan = rememberBarcodeScan()
    val previewRef = rememberPreviewViewRef()
    val controller = rememberCamera(
        isActive = isActive,
        device = device ?: position,
        outputs = listOfNotNull(
            previewOutput,
            photoOutput,
            when (streamMode) {
                StreamMode.VIDEO -> videoOutput
                StreamMode.FRAMES -> frameOutput
                StreamMode.CODES -> barcodeScan.output
                StreamMode.DEPTH -> depthOutput
            },
            objectOutput,
        ),
        zoom = zoom,
        torchMode = torchMode,
        onStarted = {
            isRunning = true
            lastError = null
        },
        onStopped = { isRunning = false },
        onError = { lastError = it.message ?: it.toString() },
    )
    val barcodeToCameraPoint = rememberBarcodeOutputPointConverter(barcodeScan.output, controller?.device)

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
        if (streamMode == StreamMode.CODES) {
            BarcodesOverlay(
                barcodes = barcodeScan.barcodes,
                toCameraPoint = barcodeToCameraPoint,
                previewRef = previewRef,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                streamStats = when (streamMode) {
                    StreamMode.FRAMES -> frameStats?.let { "Frames: $it" }
                    StreamMode.DEPTH -> depthStats?.let { "Depth: $it" }
                    else -> null
                },
                scannedCodes = scannedCodes.mapNotNull { it.value },
                barcode = barcodeScan.lastBarcode?.describe()?.takeIf { streamMode == StreamMode.CODES },
                error = lastError ?: barcodeScan.error?.takeIf { streamMode == StreamMode.CODES },
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
                        enabled = controller != null && streamMode == StreamMode.VIDEO,
                    ) { Text(if (recorder != null) "Stop" else "Record") }
                    thumbnail?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "Last photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Frames" to StreamMode.FRAMES, "Codes" to StreamMode.CODES, "Depth" to StreamMode.DEPTH)
                        .forEach { (label, mode) ->
                            ModeButton(label, mode, current = streamMode, enabled = recorder == null) { streamMode = it }
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
    streamStats: String?,
    scannedCodes: List<String>,
    barcode: String?,
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
        if (streamStats != null) Text(streamStats, color = Color.White)
        scannedCodes.forEach { Text("Object: $it", color = Color.Green) }
        if (barcode != null) Text("Barcode: $barcode", color = Color.Cyan)
        if (error != null) Text("Error: $error", color = Color.Red)
    }
}

/** Toggles [mode] on (a filled button) or off, back to [StreamMode.VIDEO]. */
@Composable
private fun ModeButton(
    label: String,
    mode: StreamMode,
    current: StreamMode,
    enabled: Boolean,
    onModeChange: (StreamMode) -> Unit,
) {
    val isSelected = mode == current
    val onClick = { onModeChange(if (isSelected) StreamMode.VIDEO else mode) }
    if (isSelected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(label) }
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
