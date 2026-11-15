---
title: Photo
parent: Guides
nav_order: 7
---

# Photo

A `CameraPhotoOutput` captures photos. Create one and connect it to the session with the other outputs:

```kotlin
val photoOutput = rememberPhotoOutput()                     // Compose
// or: val photoOutput = VisionCamera.createPhotoOutput()   // imperative

Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(photoOutput))
```

`PhotoOutputOptions` sets the `targetResolution` (`CommonResolutions.UHD_4_3` by default), the `containerFormat`
(`NATIVE`, `JPEG`, `HEIC` or `DNG`), the compression `quality` (`0.9` by default) and the `qualityPrioritization`.
The resolution is negotiated with the other outputs and constraints, so it may not be met exactly.

## Capturing to a file

```kotlin
val photoFile = photoOutput.capturePhotoToFile(CapturePhotoSettings(flashMode = FlashMode.AUTO))
println("${photoFile.filePath} (${photoFile.width}x${photoFile.height})")
```

## Capturing in memory

`capturePhoto` returns a `Photo`, which holds native memory: dispose it once you are done, for example with `use`.

```kotlin
photoOutput.capturePhoto().use { photo ->
    val bytes = photo.getFileData()   // encoded file data, including EXIF
    photo.saveToFile(path)            // or saveToTemporaryFile()
}
```

To display a photo, convert it to an `Image` with `photo.toImage()` (or `toImageAsync()`), which applies its
orientation and mirroring. Get the native image with `image.toBitmap()` on Android and `image.toUIImage()` on iOS, or
directly with `photo.toBitmap()` / `photo.toUIImage()`.

On Android, prefer `capturePhotoToFile` for formats other than JPEG: CameraX does not properly support in-memory RAW
photos yet, so `getFileData` only works for JPEG photos there, and the `HEIC` container format is not supported.

## Capture settings

`CapturePhotoSettings` sets the `flashMode` (`OFF`, `ON` or `AUTO`, `ON` requires `device.hasFlash`), the shutter
sound, red-eye reduction, distortion correction, virtual device fusion and a `Location` to embed in the EXIF
metadata. `CapturePhotoCallbacks` notifies when the capture begins and when the photo is captured, e.g. to animate a
shutter.

## Preview image

Set `PhotoOutputOptions.previewImageTargetSize` to receive a small, ready to display `Image` before the photo itself,
e.g. to update a thumbnail right away. It is delivered to `CapturePhotoCallbacks.onPreviewImageAvailable`, if
`device.supportsPreviewImage`:

```kotlin
val photoOutput = VisionCamera.createPhotoOutput(PhotoOutputOptions(previewImageTargetSize = Size(300, 400)))
val photo = photoOutput.capturePhoto(
    callbacks = CapturePhotoCallbacks(onPreviewImageAvailable = { image -> showThumbnail(image) }),
)
```
