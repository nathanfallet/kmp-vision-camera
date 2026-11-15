---
title: Skia
parent: Guides
nav_order: 15
---

# Skia Camera and Frame Processors

The `SkiaCamera` renders camera frames with [Skia](https://skia.org). You get full GPU control over every frame:
effects, shaders, shapes, bounding boxes over faces or QR codes, and more. It is the port of VisionCamera's
`<SkiaCamera />` and Skia Frame Processors.

Skia here is [Skiko](https://github.com/JetBrains/skiko), JetBrains' Kotlin bindings for Skia. It is the same Skia
that Compose Multiplatform uses on iOS, and you use it through the `org.jetbrains.skia.*` package (`Canvas`, `Paint`,
`Image`, `RuntimeEffect`...).

Frames reach Skia without any CPU copy:

- On iOS, the `CVPixelBuffer` of the frame is wrapped as Metal textures.
- On Android, the `AHardwareBuffer` of the frame is wrapped as an OpenGL ES texture.

Your drawing runs on the GPU, and the result is shown by Compose.

## Setup

```kotlin
dependencies {
    implementation("me.nathanfallet.visioncamera:skia:0.1.0")
    // For the Compose view (SkiaCamera):
    implementation("me.nathanfallet.visioncamera:skia-compose:0.1.0")
}
```

Skiko's Android artifacts are only published on JetBrains' repository, so you must add it to your
`settings.gradle.kts`, next to your other repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Skiko's Android artifacts are only published on JetBrains' repository.
        maven("https://packages.jetbrains.team/maven/p/cmp/dev") {
            mavenContent {
                includeModuleByRegex("org\\.jetbrains\\.skiko", "skiko-android.*")
            }
        }
    }
}
```

The `mavenContent` filter is optional, but it makes Gradle only look at this repository for the Skiko Android
artifacts.

Requirements:

- On Android, only `arm64-v8a` and `x86_64` are supported. Skiko ships no runtime for `armeabi-v7a` or `x86`.
- On Android, you need Android 9 (API 28) or higher, because frames are read as hardware buffers.
- On iOS, the frame pixel format must be BGRA or 8-bit / 10-bit YUV.

There is nothing else to set up in your app. The camera permissions are the same as for a regular camera.

## Using the SkiaCamera

`SkiaCamera` is a Composable. Implement `onFrame` to render the `Frame`:

```kotlin
@Composable
fun App() {
    SkiaCamera(
        device = TargetCameraPosition.BACK,
        isActive = true,
        onFrame = { frame, render ->
            // ... custom Frame processing logic
            render { (frameTexture, canvas) ->
                // ... custom drawing operations
                canvas.drawImage(frameTexture, 0f, 0f)
            }
            frame.dispose()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

`onFrame` is called for every frame, on the frame output's thread. It receives two parameters:

- The `frame` (a `Frame`), which you can use for any kind of frame processing, like face detection.
- A `render` function (a `SkiaRender`). It renders the frame into a Skia `Canvas`. You must call it once per frame,
  otherwise the view stays blank. Calling it twice throws. If you skip it on purpose (for example to throttle the
  frame rate), set `warnIfRenderSkipped = false` to silence the warning.

Always dispose the `frame` at the end of `onFrame`.

The `render` function gives your draw lambda a `SkiaOnFrameState`, which you can destructure:

- `frameTexture`: the frame as a GPU `Image`. It is only valid inside the draw lambda.
- `canvas`: a GPU `Canvas` the size of the upright frame. It is already counter-rotated and un-mirrored, so drawing
  `frameTexture` at `(0, 0)` draws it upright.

The canvas is in the frame's coordinate system. The final result is scaled to cover the view (centered and cropped).

### Differences to a regular Camera

The `SkiaCamera` is different from the `Camera` composable in two ways:

- It has no native preview view. It draws the frames you render with `render`, in a Compose `Canvas`.
- It always has a `CameraFrameOutput` attached. You can add other outputs (photo, video...) with `outputs`.

It takes the same session parameters as `Camera`: `device`, `isActive`, `constraints`, `zoom`, `exposure`,
`torchMode`, `controllerConfiguration`, `sessionConfiguration`, and the callbacks (`onStarted`, `onError`,
`onUIRotationChanged`...). It also has a few Skia-specific parameters:

| Parameter | Description |
| --- | --- |
| `pixelFormat` | The pixel format of the frame output. Defaults to `YUV` on iOS and `NATIVE` on Android. |
| `targetResolution` | The target size of the frames. It is negotiated with the constraints and outputs. |
| `enablePreviewSizedOutputBuffers` | Downscales frames to the screen size. Faster with high-resolution sessions. |
| `enablePhysicalBufferRotation` | Rotates the buffers in the camera pipeline. Off by default: Skia does it on the GPU. |
| `warnIfRenderSkipped` | Warns if `onFrame` does not call `render`. |
| `ref` | A `SkiaCameraRef`, see below. |

Front cameras are mirrored. The Skia frame output always follows the interface orientation.

### Pixel formats

The pixel format changes the performance of your Skia camera, and the pixel format of the `frame`.

- Use `TargetVideoPixelFormat.YUV` as a good default. Skia renders 8-bit and 10-bit YUV buffers, and most frame
  processing libraries support YUV.
- Use `TargetVideoPixelFormat.RGB` as a fallback if your frame processing library does not support YUV. It costs a
  conversion.
- Use `TargetVideoPixelFormat.NATIVE` only if the negotiated `nativePixelFormat` of the `CameraSessionConfig` can be
  rendered by Skia. It is a zero-copy, GPU-only path. Skia renders 8-bit and 10-bit YUV, RGB formats and, on Android,
  `PRIVATE`. A RAW format, for example, does not work.

See [the frame output guide](frame-output.md) for more about pixel formats.

## Drawing with shaders

Write your effect in SkSL (Skia's shading language) and compile it with `RuntimeEffect.makeForShader`. The shader
samples the frame with `src.eval(...)`:

```kotlin
// Compile the shader and create the paint once, not on every frame
val effect = remember {
    RuntimeEffect.makeForShader(
        """
        uniform shader src;
        half4 main(float2 xy) {
            half4 color = src.eval(xy);
            half gray = dot(color.rgb, half3(0.299, 0.587, 0.114));
            return half4(gray, gray, gray, color.a);
        }
        """.trimIndent(),
    )
}
val paint = remember { Paint() }

SkiaCamera(
    device = TargetCameraPosition.BACK,
    isActive = true,
    onFrame = { frame, render ->
        render { (frameTexture, canvas) ->
            // Use the frame as the input of the shader
            val imageShader = frameTexture.makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                SamplingMode.LINEAR,
            )
            paint.shader = effect.makeShader(null, arrayOf(imageShader), null)
            // Draw a rect covering the frame with the shader paint
            canvas.drawRect(Rect.makeWH(frameTexture.width.toFloat(), frameTexture.height.toFloat()), paint)
        }
        frame.dispose()
    },
    modifier = Modifier.fillMaxSize(),
)
```

> Skia ignores the shader of a `Paint` passed to `drawImage`. To apply a shader that samples the frame, draw a rect
> that covers the `frameTexture` with the shader paint, as above.

The sample app has a [Skia screen](https://github.com/NathanFallet/kmp-vision-camera/blob/main/sample/src/commonMain/kotlin/me/nathanfallet/visioncamera/sample/SkiaScreen.kt)
with grayscale, invert and pixelate shaders.

## Drawing overlays

You can draw anything on the canvas, after (or before) the frame. For example, to draw a box over every detected
object, in the frame's coordinates:

```kotlin
render { (frameTexture, canvas) ->
    canvas.drawImage(frameTexture, 0f, 0f)
    for (box in boxes) {
        canvas.drawRect(
            Rect.makeXYWH(
                box.x * frameTexture.width,
                box.y * frameTexture.height,
                box.width * frameTexture.width,
                box.height * frameTexture.height,
            ),
            boxPaint,
        )
    }
}
```

Since you control the rendering, you can run your analysis in `onFrame` first (for example with the
[barcode scanner](barcode-scanner.md) or an ML model) and draw the results right on the frame.

## The SkiaCameraRef

Create a `SkiaCameraRef` with `rememberSkiaCameraRef` and pass it to `SkiaCamera` with `ref`. It exposes:

- `controller`: the current `CameraController`, or `null` until the camera is ready.
- `canvas`: the `GraphicsLayer` the rendered frames are drawn into, for example to capture what is on screen with
  `canvas?.toImageBitmap()`.
- `focusTo(viewPoint, options)`: focuses on a point of the view, in Compose pixels. It is a `suspend` function.
- `resetFocus()`: resets the focus. It is a `suspend` function.
- `takeSnapshot()`: returns the currently rendered content as an `Image` (or `null`). Call it on the main thread, and
  close the image when you are done.
- `convertViewPointToNormalizedPoint(viewPoint)`: converts a point of the view to a normalized `Point` (`0..1`) in the
  camera's coordinate system.

```kotlin
val camera = rememberSkiaCameraRef()
val scope = rememberCoroutineScope()

SkiaCamera(
    device = TargetCameraPosition.BACK,
    isActive = true,
    ref = camera,
    onFrame = { frame, render ->
        render { (frameTexture, canvas) -> canvas.drawImage(frameTexture, 0f, 0f) }
        frame.dispose()
    },
    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { offset -> scope.launch { camera.focusTo(offset) } }
    },
)

Button(onClick = {
    val snapshot = camera.takeSnapshot()
    println("Captured Skia snapshot! ${snapshot?.imageInfo}")
    snapshot?.close()
}) { Text("Snapshot") }
```

`focusTo`, `resetFocus` and `convertViewPointToNormalizedPoint` throw a `VisionCameraException` if the camera is not
ready, or if no frame has been rendered yet.

## Rendering yourself

You can render frames without the `SkiaCamera`, for example into your own output. The `skia` module has no Compose
dependency.

`renderToTexture` renders a `Frame` into an offscreen GPU canvas and returns the result as an `Image`:

```kotlin
val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
    frame.use {
        val image = renderToTexture(it) { (frameTexture, canvas) ->
            canvas.drawImage(frameTexture, 0f, 0f)
        }
        // ... use the image
        image.close()
    }
}
```

The frame needs a native buffer (see `Frame.hasNativeBuffer`). Some things to know:

- The returned image is a GPU image that belongs to the calling thread. Use it on this thread, and close it when you
  are done.
- To use the image on any thread (display it, encode it), copy it to the CPU with `image.makeNonTextureImage()`. Call
  it on the thread that rendered the image.
- The offscreen surfaces are cached per thread and size, and released after 15 seconds of inactivity. `SkiaCamera`
  clears them when it leaves the composition. If you use `renderToTexture` directly, call `clearSurfacesCache()` when
  you are done, to free GPU memory right away.

### Transforming points

`transformPoint` converts a normalized point (`0..1`) with a `Transform`, made of an `orientation` and an
`isMirrored` flag. For example, to go from an upright view to the camera's coordinate system:

```kotlin
val cameraPoint = transformPoint(Point(0.5, 0.5), Transform(frame.orientation, frame.isMirrored))
```

`SkiaCameraRef.convertViewPointToNormalizedPoint` does this for you, using the last rendered frame.
