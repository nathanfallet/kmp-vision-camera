# KMP Vision Camera

[![License](https://img.shields.io/github/license/nathanfallet/kmp-vision-camera)](LICENSE)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.nathanfallet.visioncamera/core)](https://klibs.io/project/nathanfallet/kmp-vision-camera)
[![Issues](https://img.shields.io/github/issues/nathanfallet/kmp-vision-camera)]()
[![Pull Requests](https://img.shields.io/github/issues-pr/nathanfallet/kmp-vision-camera)]()
[![codecov](https://codecov.io/github/nathanfallet/kmp-vision-camera/branch/main/graph/badge.svg)](https://codecov.io/github/nathanfallet/kmp-vision-camera)
[![CodeFactor](https://www.codefactor.io/repository/github/nathanfallet/kmp-vision-camera/badge)](https://www.codefactor.io/repository/github/nathanfallet/kmp-vision-camera)

> This project is a Kotlin Multiplatform port of [React Native VisionCamera](https://github.com/mrousavy/react-native-vision-camera)
> (V5) by [Marc Rousavy](https://github.com/mrousavy) / [Margelo](https://margelo.com). It keeps the same concepts and
> the same API, with Kotlin coroutines instead of Promises and Compose instead of React: if you know VisionCamera, you
> already know KMP Vision Camera. It is not affiliated with, nor endorsed by, Margelo or the VisionCamera authors.

KMP Vision Camera is a high-performance camera library for Kotlin Multiplatform (Android and iOS). It gives access to
the full power of the native camera APIs (CameraX and Camera2 on Android, AVFoundation on iOS) through a single
Kotlin API, from a simple preview to advanced session configuration, photo, video and real-time frame processing.

* **Documentation:** [visioncamera.nathanfallet.me](https://visioncamera.nathanfallet.me)
* **API reference:** [visioncamera.nathanfallet.me/api](https://visioncamera.nathanfallet.me/api)
* **Repository:** [github.com/nathanfallet/kmp-vision-camera](https://github.com/nathanfallet/kmp-vision-camera)

## Features

* **VisionCamera's API in Kotlin** – `VisionCamera`, `CameraDevice`, `CameraSession`, outputs, constraints,
  `CameraController`… the same names and concepts, so the VisionCamera documentation and knowledge apply.
* **Native engines** – CameraX (with Camera2 interop) on Android, AVFoundation on iOS, with all devices and their
  capabilities exposed: physical lenses, formats, FPS ranges, HDR, zoom, torch, exposure…
* **Constraints** – describe what you want (60 FPS, HDR, stabilization, resolution priority…), and the session
  negotiates the closest configuration the device supports.
* **Frame processing** – a real-time `Frame` stream on a dedicated thread, with planes, CPU pixel buffers and
  zero-copy native buffers (`CVPixelBufferRef` on iOS, `AHardwareBuffer*` on Android), plus depth frames, a frame
  renderer and a frame converter.
* **Barcode scanning** – the `barcode` module ports VisionCamera's barcode scanner, with the same API on both
  platforms: ML Kit on Android, Apple Vision on iOS (nothing to add to your app).
* **Location** – the `location` module ports VisionCamera's location: a `LocationManager` (Google Play Services on
  Android, CoreLocation on iOS) and location tags in photos and videos.
* **Frame resizer** – the `resizer` module resizes and converts frames to RGB tensors for ML models on the GPU, with
  Metal on iOS and Vulkan on Android.
* **Skia** – the `skia` and `skia-compose` modules port VisionCamera's `<SkiaCamera />`: render frames with Skia
  (Skiko) on the GPU, with SkSL shaders and custom drawing. Frames reach Skia without a CPU copy.
* **Coroutines first** – every asynchronous operation is a `suspend` function.
* **Compose optional** – the engine has no Compose dependency and works with Android Views, UIKit or SwiftUI; the
  `compose` module adds `Camera`, `NativePreviewView` and `remember*` helpers.

## Status

Early development. What works today:

- [x] Permissions (camera and microphone)
- [x] Camera devices: listing, default devices, capabilities, formats, physical devices
- [x] Camera sessions: configuration with connections and constraints, start/stop, lifecycle, interruptions
- [x] Camera controller: zoom, torch, exposure bias, low-light boost, focus and metering, manual controls
- [x] Preview output and preview views (Android `View`, iOS `UIView`, Compose), tap-to-focus and pinch-to-zoom
- [x] Photo output
- [x] Video output
- [x] Photo preview image and preview snapshots
- [x] Frame output (frame processing) with native buffers, frame renderer and frame converter
- [x] Depth output, object output (iOS) and output synchronizer (iOS)
- [x] Automatic output orientation (device or interface), subject area changes
- [x] Barcode scanner (`barcode`, `barcode-compose`): frames, images, camera output and `CodeScanner`
- [x] Location (`location`, `location-compose`): `LocationManager`, `rememberLocation`, location tags in photos and videos
- [x] Resizer (`resizer`, `resizer-compose`): GPU resize and RGB conversion of frames for ML models (Metal, Vulkan)
- [x] Skia (`skia`, `skia-compose`): `SkiaCamera` and Skia frame processors with Skiko (SkSL shaders, custom drawing)

## Modules

| Module | Description |
|---|---|
| `core` | The camera engine: devices, sessions, controller, preview, photo, video, frame, depth and object outputs |
| `compose` | Compose Multiplatform integration: `Camera`, `NativePreviewView`, `NativeFrameRendererView`, `remember*` |
| `barcode` | Barcode scanning on frames, images or as a camera output |
| `barcode-compose` | Compose integration of `barcode`: `CodeScanner`, `rememberBarcodeScanner`, `rememberBarcodeScannerOutput` |
| `location` | Device location (`LocationManager`) to embed in photos and videos |
| `location-compose` | Compose integration of `location`: `rememberLocation`, `rememberLocationManager` |
| `resizer` | GPU frame resizer and RGB converter for ML models, with Metal and Vulkan |
| `resizer-compose` | Compose integration of `resizer`: `rememberResizer` |
| `skia` | Renders frames as Skia (Skiko) GPU textures: `renderToTexture` |
| `skia-compose` | Compose integration of `skia`: `SkiaCamera`, `rememberSkiaCameraRef` |

## Installation

Add the dependencies to your `build.gradle.kts`, in the `commonMain` source set:

```kotlin
dependencies {
    implementation("me.nathanfallet.visioncamera:core:0.1.0")
    // Optional, for Compose Multiplatform:
    implementation("me.nathanfallet.visioncamera:compose:0.1.0")
    // Optional, for barcode scanning:
    implementation("me.nathanfallet.visioncamera:barcode:0.1.0")
    // Optional, for barcode scanning with Compose Multiplatform:
    implementation("me.nathanfallet.visioncamera:barcode-compose:0.1.0")
    // Optional, for location tags in photos and videos (and its Compose helpers):
    implementation("me.nathanfallet.visioncamera:location:0.1.0")
    implementation("me.nathanfallet.visioncamera:location-compose:0.1.0")
    // Optional, for resizing frames on the GPU (and its Compose helper):
    implementation("me.nathanfallet.visioncamera:resizer:0.1.0")
    implementation("me.nathanfallet.visioncamera:resizer-compose:0.1.0")
    // Optional, for rendering frames with Skia (and the SkiaCamera Compose view):
    implementation("me.nathanfallet.visioncamera:skia:0.1.0")
    implementation("me.nathanfallet.visioncamera:skia-compose:0.1.0")
}
```

The `skia` modules use Skiko, whose Android artifacts are only published on JetBrains' repository. Add it to the
`dependencyResolutionManagement` of your `settings.gradle.kts`:

```kotlin
maven("https://packages.jetbrains.team/maven/p/cmp/dev") {
    mavenContent {
        includeModuleByRegex("org\\.jetbrains\\.skiko", "skiko-android.*")
    }
}
```

Skia supports `arm64-v8a` and `x86_64` on Android 9 (API 28) or higher.

On Android, declare the permissions you need in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<!-- Only if you record audio -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

On iOS, add `NSCameraUsageDescription` (and `NSMicrophoneUsageDescription` if you record audio) to your
`Info.plist`. For location tags, add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` on Android, and
`NSLocationWhenInUseUsageDescription` on iOS.

## Usage

### Compose

```kotlin
@Composable
fun CameraScreen() {
    val permission = rememberCameraPermission()
    LaunchedEffect(permission.hasPermission) {
        if (!permission.hasPermission && permission.canRequestPermission) permission.requestPermission()
    }
    if (!permission.hasPermission) return

    Camera(
        device = TargetCameraPosition.BACK,
        isActive = true,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Or, with more control over the device and the outputs:

```kotlin
val device = rememberCameraDevice(TargetCameraPosition.BACK)
val previewOutput = rememberPreviewOutput()
val controller = rememberCamera(
    isActive = true,
    device = device ?: TargetCameraPosition.BACK,
    outputs = listOf(previewOutput),
    constraints = listOf(FPSConstraint(60.0)),
)
NativePreviewView(previewOutput, Modifier.fillMaxSize())
```

### Frame processing

```kotlin
val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
    frame.use {
        // Runs on the output's thread, for every frame
        println("${it.width}x${it.height}, ${it.getPlanes().size} planes")
    }
}
Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(frameOutput))
```

### Barcode scanning

```kotlin
CodeScanner(
    isActive = true,
    barcodeFormats = listOf(TargetBarcodeFormat.QR_CODE, TargetBarcodeFormat.EAN_13),
    onBarcodeScanned = { barcodes -> barcodes.forEach { println(it.rawValue) } },
    onError = { println(it) },
    modifier = Modifier.fillMaxSize(),
)
```

Or, as an output of your own `Camera` (or with `rememberBarcodeScanner` in a frame processor):

```kotlin
val barcodeOutput = rememberBarcodeScannerOutput(
    barcodeFormats = listOf(TargetBarcodeFormat.ALL_FORMATS),
    onBarcodeScanned = { barcodes -> barcodes.forEach { println("${it.format}: ${it.rawValue}") } },
    onError = { println(it) },
)
Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(barcodeOutput))
```

### Location

```kotlin
val location = rememberLocation()
LaunchedEffect(location.hasPermission) {
    if (!location.hasPermission) location.requestPermission()
}

// Embed it in photos and videos
val photo = photoOutput.capturePhoto(CapturePhotoSettings(location = location.currentLocation))
```

### Resizing frames for ML models

```kotlin
val resizer = rememberResizer(
    ResizerOptions(
        width = 192,
        height = 192,
        channelOrder = ChannelOrder.RGB,
        dataType = DataType.FLOAT32,
        scaleMode = ScaleMode.COVER,
        pixelLayout = PixelLayout.PLANAR,
    ),
).resizer
val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
    frame.use {
        resizer?.resize(it)?.use { resized ->
            val pixels = resized.getPixelBuffer() // 192x192 RGB float32, planar
        }
    }
}
```

### Skia

```kotlin
val paint = remember { Paint() }
val effect = remember {
    RuntimeEffect.makeForShader(
        """
        uniform shader src;
        half4 main(float2 xy) {
            half4 color = src.eval(xy);
            return half4(1.0 - color.rgb, color.a);
        }
        """.trimIndent(),
    )
}
SkiaCamera(
    device = TargetCameraPosition.BACK,
    isActive = true,
    onFrame = { frame, render ->
        render { (frameTexture, canvas) ->
            val shader = frameTexture.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
            paint.shader = effect.makeShader(null, arrayOf(shader), null)
            // Skia ignores the shader of a paint given to drawImage: draw a rect covering the frame
            canvas.drawRect(Rect.makeWH(frameTexture.width.toFloat(), frameTexture.height.toFloat()), paint)
        }
        frame.dispose()
    },
    modifier = Modifier.fillMaxSize(),
)
```

### Imperative API

The engine has no Compose dependency, and can be used from any Kotlin code (Android Views, UIKit, SwiftUI…):

```kotlin
if (VisionCamera.cameraPermissionStatus != PermissionStatus.AUTHORIZED) {
    VisionCamera.requestCameraPermission()
}

val deviceFactory = VisionCamera.createDeviceFactory()
val device = getCameraDevice(deviceFactory, TargetCameraPosition.BACK)!!
val previewOutput = VisionCamera.createPreviewOutput()

val session = VisionCamera.createCameraSession(enableMultiCam = false)
val (controller) = session.configure(
    listOf(
        CameraSessionConnection(
            input = device,
            outputs = listOf(CameraOutputConfiguration(previewOutput)),
            constraints = listOf(FPSConstraint(60.0)),
        ),
    ),
)
session.start()
controller.setZoom(2.0)

// Display it in a PreviewView (an Android View, or a UIView on iOS)
previewView.previewOutput = previewOutput
```

## Documentation

Guides are available at [visioncamera.nathanfallet.me](https://visioncamera.nathanfallet.me). Since the API mirrors
VisionCamera's, the [VisionCamera documentation](https://visioncamera.margelo.com) is also a great resource.

## Sample app

The [sample](sample) module contains a demo app for Android ([sample-android](sample-android)) and iOS
([sample-ios](sample-ios)): preview with tap-to-focus and pinch-to-zoom, photo, video recording, live frame
statistics, and QR code scanning on iOS.

## Credits

KMP Vision Camera is a port of [React Native VisionCamera](https://github.com/mrousavy/react-native-vision-camera),
licensed under the MIT license, Copyright (c) 2025 Marc Rousavy. Its architecture, API design and
native implementation are the foundation of this project. Its license notice is reproduced in [NOTICE](NOTICE).

## License

KMP Vision Camera is licensed under the [Apache License 2.0](LICENSE).
