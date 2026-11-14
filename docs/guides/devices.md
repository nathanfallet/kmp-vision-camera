---
title: Camera Devices
parent: Guides
nav_order: 1
---

# Camera Devices

A `CameraDevice` is a physical camera (e.g. the 0.5x ultra-wide-angle camera) or a virtual camera made of several
physical ones (e.g. the Triple-Camera, which switches between its lenses while zooming).

## Getting a device

Most apps only need the default camera at a position. Pass a `TargetCameraPosition` anywhere a device is expected,
and the default device at that position is used:

```kotlin
Camera(device = TargetCameraPosition.BACK, isActive = true)
```

To pick a device yourself, use `rememberCameraDevice` in Compose, or `getCameraDevice` with a `CameraDeviceFactory`:

```kotlin
// Compose
val device = rememberCameraDevice(TargetCameraPosition.BACK)

// Anywhere
val deviceFactory = VisionCamera.createDeviceFactory()
val device = getCameraDevice(deviceFactory, TargetCameraPosition.BACK)
```

A `DeviceFilter` ranks devices by the physical cameras they are made of. A simple device starts faster, a virtual
device can switch lenses while zooming:

```kotlin
// Faster to start
val wide = getCameraDevice(deviceFactory, TargetCameraPosition.BACK, DeviceFilter(listOf(DeviceType.WIDE_ANGLE)))
// Zooms from 0.5x to 3x
val triple = getCameraDevice(
    deviceFactory,
    TargetCameraPosition.BACK,
    DeviceFilter(listOf(DeviceType.ULTRA_WIDE_ANGLE, DeviceType.WIDE_ANGLE, DeviceType.TELEPHOTO)),
)
```

The full list is available with `rememberCameraDevices()` or `deviceFactory.cameraDevices`, and changes when
external cameras are plugged in or out (`addOnCameraDevicesChangedListener`).

## Capabilities

A `CameraDevice` describes what it supports individually: `supportedFPSRanges`, `supportedVideoDynamicRanges`,
`supportsPhotoHDR`, `getSupportedResolutions(...)`, `minZoom`/`maxZoom`, `hasTorch`, `supportsLowLightBoost`,
`physicalDevices`…

Features are not always supported together (for example 10-bit HDR video at 120 FPS). The combination is negotiated
by the session with [constraints](constraints.md).
