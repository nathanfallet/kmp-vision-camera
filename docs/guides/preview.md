---
title: Preview
parent: Guides
nav_order: 5
---

# Preview

A `CameraPreviewOutput` streams the camera feed to a preview view.

With Compose, `Camera` displays its own preview. To use your own outputs, create a preview output and display it with
`NativePreviewView`:

```kotlin
val previewOutput = rememberPreviewOutput()
rememberCamera(isActive = true, device = TargetCameraPosition.BACK, outputs = listOf(previewOutput))
NativePreviewView(
    previewOutput = previewOutput,
    modifier = Modifier.fillMaxSize(),
    resizeMode = PreviewResizeMode.COVER,
)
```

Without Compose, use the platform `PreviewView` (an Android `View`, or a `UIView` on iOS) and set its
`previewOutput`.

- `resizeMode`: `COVER` fills the view and crops the feed, `CONTAIN` fits the whole feed in the view.
- `implementationMode` (Android only): `PERFORMANCE` uses a `SurfaceView`, `COMPATIBLE` uses a `TextureView` that
  supports transformations and animations.

## Snapshots

On Android, `takeSnapshot()` (on the `PreviewView`, a `PreviewViewRef` or a `CameraRef`) returns the preview's current
contents as an `Image`, much faster than a photo capture. Like VisionCamera, it is not available on iOS and throws.
