package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.nathanfallet.visioncamera.CameraDepthFrameOutput
import me.nathanfallet.visioncamera.CameraFrameOutput
import me.nathanfallet.visioncamera.CameraObjectOutput
import me.nathanfallet.visioncamera.CameraOrientation
import me.nathanfallet.visioncamera.Depth
import me.nathanfallet.visioncamera.DepthFrameOutputOptions
import me.nathanfallet.visioncamera.Frame
import me.nathanfallet.visioncamera.FrameDroppedReason
import me.nathanfallet.visioncamera.FrameOutputOptions
import me.nathanfallet.visioncamera.FrameRenderer
import me.nathanfallet.visioncamera.ObjectOutputOptions
import me.nathanfallet.visioncamera.OrientationSource
import me.nathanfallet.visioncamera.ScannedObject
import me.nathanfallet.visioncamera.ScannedObjectType
import me.nathanfallet.visioncamera.VisionCamera

/**
 * Create a [CameraFrameOutput] with the given [options], kept across recompositions and recreated when the [options]
 * change (the equivalent of VisionCamera's `useFrameOutput`).
 *
 * [onFrame] runs on the output's thread (not the main thread) for every [Frame], and must dispose it as soon as
 * possible. The latest [onFrame] is always used, without recreating the output.
 *
 * ```kotlin
 * val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
 *     frame.use {
 *         // processing...
 *     }
 * }
 * Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(frameOutput))
 * ```
 *
 * [onFrameDropped] is called when a [Frame] has been dropped, e.g. because [onFrame] takes longer than a frame
 * interval.
 */
@Composable
fun rememberFrameOutput(
    options: FrameOutputOptions = FrameOutputOptions(),
    onFrameDropped: ((reason: FrameDroppedReason) -> Unit)? = null,
    onFrame: ((frame: Frame) -> Unit)?,
): CameraFrameOutput {
    val frameOutput = remember(options) { VisionCamera.createFrameOutput(options) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnFrameDropped by rememberUpdatedState(onFrameDropped)
    val hasOnFrame = onFrame != null
    DisposableEffect(frameOutput, hasOnFrame) {
        frameOutput.setOnFrameCallback(
            if (hasOnFrame) {
                { frame -> currentOnFrame?.invoke(frame) ?: frame.dispose() }
            } else {
                null
            },
        )
        onDispose { frameOutput.setOnFrameCallback(null) }
    }
    DisposableEffect(frameOutput) {
        frameOutput.setOnFrameDroppedCallback { reason -> currentOnFrameDropped?.invoke(reason) }
        onDispose { frameOutput.setOnFrameDroppedCallback(null) }
    }
    return frameOutput
}

/**
 * Create a [CameraDepthFrameOutput] with the given [options], kept across recompositions and recreated when the
 * [options] change (the equivalent of VisionCamera's `useDepthOutput`).
 *
 * [onDepth] runs on the output's thread for every [Depth] frame, and must dispose it as soon as possible.
 * [onDepthFrameDropped] is called when a [Depth] frame has been dropped.
 */
@Composable
fun rememberDepthOutput(
    options: DepthFrameOutputOptions = DepthFrameOutputOptions(),
    onDepthFrameDropped: ((reason: FrameDroppedReason) -> Unit)? = null,
    onDepth: ((depth: Depth) -> Unit)?,
): CameraDepthFrameOutput {
    val depthOutput = remember(options) { VisionCamera.createDepthFrameOutput(options) }
    val currentOnDepth by rememberUpdatedState(onDepth)
    val currentOnDepthFrameDropped by rememberUpdatedState(onDepthFrameDropped)
    val hasOnDepth = onDepth != null
    DisposableEffect(depthOutput, hasOnDepth) {
        depthOutput.setOnDepthFrameCallback(
            if (hasOnDepth) {
                { depth -> currentOnDepth?.invoke(depth) ?: depth.dispose() }
            } else {
                null
            },
        )
        onDispose { depthOutput.setOnDepthFrameCallback(null) }
    }
    DisposableEffect(depthOutput) {
        depthOutput.setOnDepthFrameDroppedCallback { reason -> currentOnDepthFrameDropped?.invoke(reason) }
        onDispose { depthOutput.setOnDepthFrameDroppedCallback(null) }
    }
    return depthOutput
}

/**
 * Create a [CameraObjectOutput] scanning the given [types], kept across recompositions and recreated when the
 * [types] change (the equivalent of VisionCamera's `useObjectOutput`). iOS only.
 *
 * ```kotlin
 * val objectOutput = rememberObjectOutput(listOf(ScannedObjectType.QR)) { objects ->
 *     codes = objects.filterIsInstance<ScannedCode>()
 * }
 * ```
 */
@Composable
fun rememberObjectOutput(
    types: List<ScannedObjectType>,
    onObjectsScanned: ((objects: List<ScannedObject>) -> Unit)?,
): CameraObjectOutput {
    val objectOutput = remember(types) { VisionCamera.createObjectOutput(ObjectOutputOptions(types)) }
    val currentOnObjectsScanned by rememberUpdatedState(onObjectsScanned)
    DisposableEffect(objectOutput) {
        objectOutput.setOnObjectsScannedCallback { objects -> currentOnObjectsScanned?.invoke(objects) }
        onDispose { objectOutput.setOnObjectsScannedCallback(null) }
    }
    return objectOutput
}

/**
 * Create a [FrameRenderer], kept across recompositions (the equivalent of VisionCamera's `useFrameRenderer`).
 */
@Composable
fun rememberFrameRenderer(): FrameRenderer = remember { VisionCamera.createFrameRenderer() }

/**
 * The current [CameraOrientation] of the given [source], updated on every change, or `null` if it is not known yet or
 * [source] is `null` (the equivalent of VisionCamera's `useOrientation`).
 */
@Composable
fun rememberOrientation(source: OrientationSource?): CameraOrientation? {
    val orientationManager = remember(source) { source?.let { VisionCamera.createOrientationManager(it) } }
    var orientation by remember(orientationManager) { mutableStateOf(orientationManager?.currentOrientation) }
    DisposableEffect(orientationManager) {
        orientationManager?.startOrientationUpdates { orientation = it }
        onDispose { orientationManager?.stopOrientationUpdates() }
    }
    return orientation
}
