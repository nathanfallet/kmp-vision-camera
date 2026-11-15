---
title: Camera Controller
parent: Guides
nav_order: 6
---

# Camera Controller

A `CameraController` controls a device connected to a session. Get it from `rememberCamera` or
`CameraSession.configure`.

```kotlin
controller.setZoom(2.0)                    // between minZoom and maxZoom
controller.setTorchMode(TorchMode.ON)      // requires device.hasTorch
controller.setExposureBias(1.0)            // between device.minExposureBias and maxExposureBias
controller.configure(CameraControllerConfiguration(enableLowLightBoost = true))
```

With Compose, `zoom`, `exposure` and `torchMode` can also be passed declaratively to `rememberCamera` and `Camera`.

`displayableZoomFactor` is the zoom to show to the user: on a Triple-Camera, a `zoom` of `1` is displayed as `0.5x`.

## Focus

`focusTo` meters AE (exposure), AF (focus) and AWB (white balance) on a `MeteringPoint`, and suspends until focusing
has settled. Create the point from a tap on the preview, or from normalized coordinates:

```kotlin
val point = VisionCamera.createNormalizedMeteringPoint(0.5, 0.5)
controller.focusTo(point, FocusOptions(responsiveness = FocusResponsiveness.SNAPPY))
controller.resetFocus()
```

`FocusOptions` chooses the metering `modes` (all supported ones by default), the `responsiveness`, whether the values
stay locked after settling (`adaptiveness`), and `autoResetAfter` (5 seconds by default).

With Compose, `Camera(enableNativeTapToFocusGesture = true)` installs the native tap-to-focus gesture. To focus from
your own gestures, use a `CameraRef`:

```kotlin
val camera = rememberCameraRef()
val scope = rememberCoroutineScope()
Camera(
    device = TargetCameraPosition.BACK,
    isActive = true,
    ref = camera,
    modifier = Modifier.pointerInput(Unit) {
        detectTapGestures { offset -> scope.launch { camera.focusTo(offset) } }
    },
)
```

## Zoom gesture and animation

`Camera(enableNativeZoomGesture = true)` installs the native pinch-to-zoom gesture (`zoom` must then be `null`).
`startZoomAnimation(zoom, rate)` animates the zoom, and `cancelZoomAnimation` stops it. Outside of `Camera`, create
the gestures with `rememberGestureControllers` (or `VisionCamera.createZoomGestureController` and
`createTapToFocusGestureController`) and attach them to a preview view's `gestureControllers`.

## Manual controls (iOS)

On iOS, the focus, exposure and white balance can also be locked manually: `setFocusLocked(lensPosition)`,
`setExposureLocked(duration, iso)`, `setWhiteBalanceLocked(gains)`, or `lockCurrentFocus`, `lockCurrentExposure`
and `lockCurrentWhiteBalance` to keep the current values. `resetFocus` returns to continuous auto modes.
`enableTorchWithStrength` turns the torch on at a custom strength where `device.supportsTorchStrength`.

