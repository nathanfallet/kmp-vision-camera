package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.nathanfallet.visioncamera.TargetCameraPosition
import me.nathanfallet.visioncamera.skia.compose.SkiaCamera
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.SamplingMode

/** The effects of the Skia screen: SkSL shaders sampling the camera Frame (`src`). */
private enum class SkiaEffect(val label: String, val sksl: String?) {
    NONE("None", null),
    GRAYSCALE(
        "Grayscale",
        """
        uniform shader src;
        half4 main(float2 xy) {
            half4 color = src.eval(xy);
            half gray = dot(color.rgb, half3(0.299, 0.587, 0.114));
            return half4(gray, gray, gray, color.a);
        }
        """.trimIndent(),
    ),
    INVERT(
        "Invert",
        """
        uniform shader src;
        half4 main(float2 xy) {
            half4 color = src.eval(xy);
            return half4(1.0 - color.rgb, color.a);
        }
        """.trimIndent(),
    ),
    PIXELATE(
        "Pixels",
        """
        uniform shader src;
        half4 main(float2 xy) {
            float size = 24.0;
            return src.eval((floor(xy / size) + 0.5) * size);
        }
        """.trimIndent(),
    ),
}

/**
 * A [SkiaCamera] drawing every Frame with a Skia shader (like VisionCamera's `<SkiaCamera />` examples), then a
 * small overlay drawn on the same canvas.
 */
@Composable
internal fun SkiaScreen(onClose: () -> Unit) {
    var position by remember { mutableStateOf(TargetCameraPosition.BACK) }
    var effect by remember { mutableStateOf(SkiaEffect.GRAYSCALE) }
    val currentEffect by rememberUpdatedState(effect)
    // Compiled once: the shaders are created on the Frame output's thread, in onFrame.
    val runtimeEffects = remember {
        SkiaEffect.entries.associateWith { it.sksl?.let(RuntimeEffect::makeForShader) }
    }
    val paint = remember { Paint() }
    val overlayPaint = remember { Paint().apply { color = OVERLAY_COLOR } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        SkiaCamera(
            device = position,
            isActive = true,
            onFrame = { frame, render ->
                render { (frameTexture, canvas) ->
                    val runtimeEffect = runtimeEffects[currentEffect]
                    if (runtimeEffect == null) {
                        canvas.drawImage(frameTexture, 0f, 0f)
                    } else {
                        val imageShader = frameTexture.makeShader(
                            FilterTileMode.CLAMP,
                            FilterTileMode.CLAMP,
                            SamplingMode.LINEAR,
                        )
                        paint.shader = runtimeEffect.makeShader(null, arrayOf(imageShader), null)
                        // Skia ignores the shader of a paint given to drawImage: draw a rect sampling the Frame.
                        canvas.drawRect(Rect.makeWH(frameTexture.width.toFloat(), frameTexture.height.toFloat()), paint)
                    }
                    // Drawn in the Frame's coordinates, before the counter-rotation is restored.
                    canvas.drawCircle(frameTexture.width / 2f, frameTexture.height / 2f, CIRCLE_RADIUS, overlayPaint)
                }
                frame.dispose()
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SkiaCamera: ${effect.label}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                    .padding(12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkiaEffect.entries.forEach { entry ->
                        if (entry == effect) {
                            Button(onClick = {}) { Text(entry.label) }
                        } else {
                            FilledTonalButton(onClick = { effect = entry }) { Text(entry.label) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        position = if (position == TargetCameraPosition.BACK) {
                            TargetCameraPosition.FRONT
                        } else {
                            TargetCameraPosition.BACK
                        }
                    }) { Text("Flip") }
                    FilledTonalButton(onClick = onClose) { Text("Back") }
                }
            }
        }
    }
}

private const val CIRCLE_RADIUS = 24f
private const val OVERLAY_COLOR = 0x80FF3B30.toInt()
