---
title: Object Scanning (iOS)
parent: Guides
nav_order: 11
---

# Object Scanning (iOS)

On iOS, a `CameraObjectOutput` scans codes, faces and bodies with the system's built-in detectors, without any frame
processing:

```kotlin
var codes by remember { mutableStateOf(emptyList<ScannedCode>()) }
val objectOutput = rememberObjectOutput(listOf(ScannedObjectType.QR, ScannedObjectType.EAN_13)) { objects ->
    codes = objects.filterIsInstance<ScannedCode>()
}
Camera(device = TargetCameraPosition.BACK, isActive = true, outputs = listOf(previewOutput, objectOutput))
```

Every `ScannedObject` has a `type` and a `boundingBox`:

- A `ScannedCode` also has its decoded `value` and its `cornerPoints`.
- A `ScannedFace` has its `faceID` and its `rollAngle` and `yawAngle`, when they are available.

Coordinates are in camera space (`0.0` to `1.0`). To draw them on the screen, convert the whole object with
`convertScannedObjectCoordinatesToViewCoordinates`, on `PreviewViewRef` or `CameraRef` with Compose. It takes the
preview's scaling and cropping into account, and returns the same kind of object with its bounding box and corner
points in view coordinates:

```kotlin
val viewCode = previewRef.convertScannedObjectCoordinatesToViewCoordinates(code) as ScannedCode
```

On Android, `createObjectOutput` throws, like in VisionCamera. Scan codes with the `barcode` module (ML Kit) on a frame
output instead (coming soon).
