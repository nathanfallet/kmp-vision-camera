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
