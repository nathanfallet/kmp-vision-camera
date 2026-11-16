package me.nathanfallet.visioncamera.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.nathanfallet.visioncamera.compose.rememberCameraPermission

/**
 * The sample app: asks for the Camera permission, then shows the camera screen.
 */
@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val permission = rememberCameraPermission()
            var showSkia by remember { mutableStateOf(false) }
            var showMulti by remember { mutableStateOf(false) }
            if (permission.hasPermission) {
                if (showSkia) {
                    SkiaScreen(onClose = { showSkia = false })
                } else if (showMulti) {
                    MultiCamScreen(onClose = { showMulti = false })
                } else {
                    CameraScreen(onOpenSkia = { showSkia = true }, onOpenMulti = { showMulti = true })
                }
            } else {
                PermissionScreen(
                    canRequestPermission = permission.canRequestPermission,
                    requestPermission = { permission.requestPermission() },
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    canRequestPermission: Boolean,
    requestPermission: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("KMP Vision Camera", style = MaterialTheme.typography.headlineMedium)
            if (canRequestPermission) {
                Text("The sample needs the Camera permission to show the camera.", textAlign = TextAlign.Center)
                Button(onClick = { scope.launch { requestPermission() } }) {
                    Text("Grant Camera permission")
                }
            } else {
                Text(
                    "The Camera permission has been denied. Grant it from the system Settings.",
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
