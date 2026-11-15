package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.CameraPhotoOutput
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.CameraVideoOutput
import me.nathanfallet.visioncamera.PhotoOutputOptions
import me.nathanfallet.visioncamera.VideoOutputOptions
import me.nathanfallet.visioncamera.VisionCamera

/**
 * Create a [CameraPreviewOutput], kept across recompositions. Display it with [NativePreviewView].
 */
@Composable
fun rememberPreviewOutput(): CameraPreviewOutput = remember { VisionCamera.createPreviewOutput() }

/**
 * Create a [CameraPhotoOutput] with the given [options], kept across recompositions and recreated when the
 * [options] change (the equivalent of VisionCamera's `usePhotoOutput`).
 *
 * ```kotlin
 * val photoOutput = rememberPhotoOutput()
 * Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(photoOutput))
 * // later, e.g. in a button's coroutine:
 * val photoFile = photoOutput.capturePhotoToFile()
 * ```
 */
@Composable
fun rememberPhotoOutput(options: PhotoOutputOptions = PhotoOutputOptions()): CameraPhotoOutput =
    remember(options) { VisionCamera.createPhotoOutput(options) }

/**
 * Create a [CameraVideoOutput] with the given [options], kept across recompositions and recreated when the
 * [options] change (the equivalent of VisionCamera's `useVideoOutput`).
 */
@Composable
fun rememberVideoOutput(options: VideoOutputOptions = VideoOutputOptions()): CameraVideoOutput =
    remember(options) { VisionCamera.createVideoOutput(options) }
