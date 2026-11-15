---
title: Frame Processing
parent: Guides
nav_order: 9
---

# Frame Processing

A `CameraFrameOutput` streams every `Frame` the Camera sees, to run your own processing on it in real time: ML models,
computer vision, custom codes, filters, streaming…

```kotlin
val frameOutput = rememberFrameOutput(FrameOutputOptions(pixelFormat = TargetVideoPixelFormat.YUV)) { frame ->
    frame.use {
        // processing...
    }
}
Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(previewOutput, frameOutput))
```

Without Compose, create it with `VisionCamera.createFrameOutput(options)` and set the callback with
`frameOutput.setOnFrameCallback { frame -> ... }`.

## Lifetime of a Frame

A `Frame` is backed by a native buffer (an `ImageProxy` on Android, a `CMSampleBuffer` on iOS) from a small pool:

- **Dispose every Frame as soon as possible**, with `frame.dispose()` or `frame.use { }`. Otherwise the pipeline runs
  out of buffers and drops frames (`FrameDroppedReason.OUT_OF_BUFFERS`).
- A disposed Frame is no longer valid (`isValid` is `false`), and reading it throws.
- Don't store a Frame or share it across coroutines: copy what you need, then dispose it.

## Threads

The callback runs on the output's `thread` (a `NativeThread`), never on the main thread. With `dropFramesWhileBusy`
(the default), frames arriving while the callback is still running are dropped instead of queued, and
`onFrameDropped` tells you why (iOS only: CameraX does not report dropped frames). To continue asynchronously on the same thread, use
`frameOutput.thread.asCoroutineDispatcher()`. To update Compose state from the callback, keep it small: Compose
state can be written from any thread.

## Pixel formats

`FrameOutputOptions.pixelFormat` chooses the format of the frames:

- `NATIVE` (the default) is the most efficient, zero-copy and GPU only. Use it with `getNativeBuffer()`.
- `YUV` is the best choice to read pixels on the CPU.
- `RGB` uses about 2.6 times more bandwidth than YUV and requires conversions. Only use it when your processing really
  needs RGB.

## Reading pixels

- Planar frames (YUV, `isPlanar`) expose their planes with `getPlanes()`, each with its `width`, `height`,
  `bytesPerRow` and `getPixelBuffer()`.
- Non-planar frames (RGB) expose their pixels with `getPixelBuffer()`.
- Both return a copy, as a `ByteArray`. With the `NATIVE` pixel format on Android, CPU-readable `HardwareBuffer`s
  are read through `getPixelBuffer()` too.
- For zero-copy GPU or ML processing, use `getNativeBuffer()` when `hasNativeBuffer` is `true`. Its `pointer` is a
  `CVPixelBufferRef` on iOS and an `AHardwareBuffer*` on Android (API 28+). Release it with `release()` before
  disposing the frame.
- Android code can also reach the CameraX `ImageProxy` through `NativeFrame`.

## Orientation and coordinates

Frames are not rotated by default, because physically rotating buffers is expensive:

- `frame.orientation` and `frame.isMirrored` tell you how to read the frame.
- `enablePhysicalBufferRotation = true` rotates them for you, at a cost.
- `convertCameraPointToFramePoint` and `convertFramePointToCameraPoint` convert between the frame and the camera
  coordinate systems.
- The preview view's `convertCameraPointToViewPoint` goes from camera to screen coordinates, e.g. to draw a box
  around something detected in a frame.

`Camera` and `rememberCamera` keep every output's `outputOrientation` in sync with the device orientation
(`orientationSource = OrientationSource.DEVICE`, the default). Use `OrientationSource.INTERFACE` to follow the UI
instead, or `null` to set `outputOrientation` yourself. `onUIRotationChanged` gives the rotation, in degrees, that
camera controls need to stay upright.

## Rendering frames

A `FrameRenderer` renders frames into a `NativeFrameRendererView`, e.g. to display processed frames:

```kotlin
val renderer = rememberFrameRenderer()
val frameOutput = rememberFrameOutput { frame ->
    renderer.renderFrame(frame)
    frame.dispose()
}
NativeFrameRendererView(renderer, Modifier.fillMaxSize())
```

## Converting frames to images

`HybridFrameConverter.convertFrameToImage(frame)` and `convertDepthToImage(depth)` return an `Image`, upright and
mirrored like the frame. Use `image.toBitmap()` on Android and `image.toUIImage()` on iOS. The `Async` variants run
on a background dispatcher. Converting is expensive, so do it on a few frames only, not on every one.

## Platform differences

- On Android, `cameraIntrinsicMatrix`, `enablePreviewSizedOutputBuffers` and `allowDeferredStart` have no effect, like
  in VisionCamera.
- The object output and the output synchronizer are iOS only.
