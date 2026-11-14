package me.nathanfallet.visioncamera.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.nathanfallet.visioncamera.CameraPreviewOutput
import me.nathanfallet.visioncamera.VisionCamera

/**
 * Create a [CameraPreviewOutput], kept across recompositions. Display it with [NativePreviewView].
 */
@Composable
fun rememberPreviewOutput(): CameraPreviewOutput = remember { VisionCamera.createPreviewOutput() }
