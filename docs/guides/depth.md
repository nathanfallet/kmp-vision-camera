---
title: Depth
parent: Guides
nav_order: 10
---

# Depth

On devices that capture depth (whose `mediaTypes` contain `MediaType.DEPTH`, like the TrueDepth or LiDAR cameras on
iPhones), a `CameraDepthFrameOutput` streams `Depth` frames:

```kotlin
val depthOutput = rememberDepthOutput { depth ->
    depth.use {
        val data = it.getDepthData()
        // processing...
    }
}
```

Like a `Frame`, a `Depth` must be disposed as soon as possible. It tells its `pixelFormat` (depth or disparity), its
`depthDataAccuracy` and `depthDataQuality`, and whether it `isDepthDataFiltered` (`DepthFrameOutputOptions.enableFiltering`).
It can be rotated (`rotate`), converted to another `DepthPixelFormat` (`convert`, see `availableDepthPixelFormats`),
or converted to a `Frame` (`toFrame`), for example to render it.

On iOS, `cameraCalibrationData` gives the intrinsic and extrinsic matrices and the lens distortion center, to project
depth values into 3D space.

## Synchronizing with frames (iOS)

To get matching color and depth frames, connect both outputs to a `CameraOutputSynchronizer`, and use its callback
instead of the outputs' own:

```kotlin
val synchronizer = VisionCamera.createOutputSynchronizer(listOf(frameOutput, depthOutput))
synchronizer.setOnFramesCallback { frames ->
    val frame = frames.filterIsInstance<Frame>().firstOrNull()
    val depth = frames.filterIsInstance<Depth>().firstOrNull()
    // processing...
    frames.forEach { (it as AutoCloseable).close() }
}
```

## Depth in photos

With `CapturePhotoSettings(enableDepthData = true)` (if `photoOutput.supportsDepthDataDelivery`), the captured `Photo`
carries its `depth`. On iOS, `enableCameraCalibrationDataDelivery` adds its `calibrationData`.
