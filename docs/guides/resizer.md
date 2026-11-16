---
title: Resizing Frames
parent: Guides
nav_order: 14
---

# Resizing and Converting Frames

Most ML models are trained on RGB frames at a very small resolution, which is not something the camera natively
produces. To use these models, you must convert your `Frame` to RGB and resize it to the input tensor's resolution
first.

For example, the BlazeFace ML model declares its input tensor like this:

```
Input: [1, 3, 128, 128] (float32)
```

This is NCHW:

- `N`: number of batches = 1
- `C`: number of channels per pixel = 3 (RGB)
- `H`: height of the image = 128 pixels
- `W`: width of the image = 128 pixels
- `float32`: the data type of each value

No camera produces 128x128 `float32` RGB images, so you have to convert the frames yourself. The `resizer` module does
it on the GPU, like VisionCamera's `react-native-vision-camera-resizer`.

```kotlin
dependencies {
    implementation("me.nathanfallet.visioncamera:resizer:1.0.0")
    // For the Compose helper (rememberResizer):
    implementation("me.nathanfallet.visioncamera:resizer-compose:1.0.0")
}
```

On iOS, the pipeline uses Metal. On Android, it uses Vulkan, with a shader embedded in the library. There is nothing to
set up in your app on either platform.

## Using the resizer

Describe the output with `ResizerOptions`: `width`, `height`, `channelOrder`, `dataType`, `scaleMode` and
`pixelLayout`. All of them are required.

### In Compose

`rememberResizer` creates the `Resizer` asynchronously, keeps it across recompositions, recreates it when the options
change, and disposes it when it leaves the composition:

```kotlin
val resizerState = rememberResizer(
    ResizerOptions(
        width = 128,
        height = 128,
        channelOrder = ChannelOrder.RGB,
        dataType = DataType.FLOAT32,
        scaleMode = ScaleMode.COVER,
        pixelLayout = PixelLayout.PLANAR,
    ),
)
```

It returns a `ResizerState`, which is one of:

- `ResizerState.Loading`: the resizer is being created.
- `ResizerState.Ready`: the resizer is ready.
- `ResizerState.Error`: creating the resizer failed.

Use `resizerState.resizer` (`null` unless it is ready) and `resizerState.error` (`null` unless it failed). Then call
`resize` in your [frame output](frame-output.md) callback:

```kotlin
val resizer by rememberUpdatedState(resizerState.resizer)
val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
    frame.use {
        resizer?.resize(it)?.use { resized ->
            val pixels = resized.getPixelBuffer()
            // call BlazeFace with `pixels` now
        }
    }
}
```

### Imperative

Without Compose, call the `suspend` function `createResizer`, and dispose the `Resizer` when you are done with it. It
is `AutoCloseable`, so you can use it in a `use { }` block:

```kotlin
val resizer = createResizer(
    ResizerOptions(
        width = 128,
        height = 128,
        channelOrder = ChannelOrder.RGB,
        dataType = DataType.FLOAT32,
        scaleMode = ScaleMode.COVER,
        pixelLayout = PixelLayout.PLANAR,
    ),
)

frameOutput.setOnFrameCallback { frame ->
    frame.use {
        resizer.resize(it).use { resized ->
            val pixels = resized.getPixelBuffer()
            // call BlazeFace with `pixels` now
        }
    }
}

// Later:
resizer.dispose()
```

### The GPUFrame

`resize` returns a `GPUFrame`. It holds the resized buffer in the target size, channel order, pixel layout and data
type. You can pass it to your ML model, for example ONNX Runtime or LiteRT (TensorFlow Lite).

`getPixelBuffer()` returns the pixels as a `ByteArray`: `width * height * 3` values of the `dataType`, in the native
byte order (little-endian on every supported device). It is a copy, so it stays valid after the `GPUFrame` is disposed.

To avoid the copy, read the GPU buffer directly with a platform accessor. It is only valid until the `GPUFrame` is
disposed:

- On Android, `getPixelByteBuffer()` returns a direct `java.nio.ByteBuffer`:

  ```kotlin
  resizer.resize(frame).use { resized ->
      interpreter.run(resized.getPixelByteBuffer(), output)
  }
  ```

- On iOS, `getPixelData()` returns an `NSData`. Use its `bytes` with your ML runtime, for example a Core ML
  `MLMultiArray`:

  ```kotlin
  resizer.resize(frame).use { resized ->
      val data = resized.getPixelData()
      // run your ML model with data.bytes
  }
  ```

Both are extension functions in the `me.nathanfallet.visioncamera.resizer` package of the matching source set
(`androidMain` or `iosMain`).

> The `Resizer` has one output buffer. You must dispose the `GPUFrame` (with `dispose()` or a `use { }` block) before
> the next `resize` call, otherwise `resize` throws and the pipeline stops running. The input `Frame` is only read
> during `resize`, so you can dispose it right after.

### Availability and fallbacks

To check if the device supports the GPU pipeline, use `isResizerAvailable()` and fall back to your own CPU
implementation otherwise:

```kotlin
suspend fun getResizer(options: ResizerOptions): Resizer? =
    if (isResizerAvailable()) {
        createResizer(options)
    } else {
        // Not available on this device: fall back to a CPU implementation.
        null
    }
```

Metal is always available on iOS devices. On Android, the pipeline needs Vulkan and the `AHardwareBuffer` extensions,
which requires Android 9 (API 28) or higher. `createResizer` throws a `VisionCameraException` if it is not available.

## Input Frame constraints

### Input Frame pixel formats

The resizer reads GPU-backed buffers, so configure your frame output to stream a supported pixel format:

- On iOS, the input `Frame` must use the `yuv-420-8-bit-full` pixel format. Set
  `FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)`.
- On Android, any pixel format works, even `PRIVATE`. `PRIVATE` is the fastest.

For the best performance, the session's native pixel format should be `yuv-420-8-bit-full` on iOS and `private` on
Android. Then use `TargetVideoPixelFormat.NATIVE` for the frame output on Android, and on iOS if the native pixel
format really is `yuv-420-8-bit-full` (use `YUV` otherwise). Do not use `NATIVE` if the native format might be
something the resizer does not support, like a RAW format.

Do not use `TargetVideoPixelFormat.RGB`. It does an unnecessary conversion in the camera pipeline, and the resizer
already converts to RGB, faster, on the GPU.

### Input Frame size

The bigger the `Frame`, the more work the resizer has to do. Set the `targetResolution` of your `FrameOutputOptions` as
small as possible. The `CameraSession` negotiates all outputs and constraints together to find the best match.

## Output configurations

### Float vs Int

Most ML models are trained on `float32` data: each value is a 32-bit float from `0.0` to `1.0`. The `DataType` can be:

- `FLOAT32`: a 32-bit float from `0.0` to `1.0`.
- `FLOAT16`: a 16-bit (half precision) float from `0.0` to `1.0`.
- `UINT8`: an unsigned 8-bit integer from `0` to `255`.
- `INT8`: a signed 8-bit integer from `-128` to `127`.

GPUs and NPUs are good at floating point, but it can be worth quantizing your model to use `UINT8`. It is 4x smaller in
memory, which can improve performance and device temperature.

The `ChannelOrder` is `RGB` or `BGR`.

### Pixel layout

`pixelLayout` sets how the channels of each pixel are arranged in memory.

- `PixelLayout.INTERLEAVED` stores complete pixels next to each other, which is (N)HWC:

  ```text
  RGBRGBRGBRGB
  RGBRGBRGBRGB
  RGBRGBRGBRGB
  ```

  Use it if your model's input tensor is (N)HWC shaped, like `[1, H, W, 3]`.

- `PixelLayout.PLANAR` stores one full channel plane after the other, which is (N)CHW:

  ```text
  RRRRRRRRRRRR
  GGGGGGGGGGGG
  BBBBBBBBBBBB
  ```

  Use it if your model's input tensor is (N)CHW shaped, like `[1, 3, H, W]`.

### Scale mode

When the aspect ratio of the `Frame` does not match the output, `scaleMode` decides what happens:

- `ScaleMode.COVER` fills the output and crops what overflows.
- `ScaleMode.CONTAIN` keeps the whole frame and adds black bars around it.
- `ScaleMode.STRETCH` stretches the frame on each axis to fill the output. It does not keep the aspect ratio.

## Orientation and mirroring

The resizer automatically counter-rotates, and counter-mirrors if needed, the `Frame`, so the output is upright and not
mirrored. Keep `enablePhysicalBufferRotation` at `false` (its default) in your `FrameOutputOptions`, so the camera does not
rotate the buffers too. The GPU is typically much faster.

## Multiple resizers

You can run several `Resizer`s if you have several ML models. It is best to convert the models to the same input type,
to avoid doing the same conversion twice. A `Resizer` keeps a memory pool that can grow large, so dispose it (or use it
in a `use { }` block) when you are done.
