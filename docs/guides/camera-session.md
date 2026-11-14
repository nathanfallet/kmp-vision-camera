---
title: Camera Session
parent: Guides
nav_order: 2
---

# Camera Session

A `CameraSession` streams from inputs (`CameraDevice`s) to outputs (`CameraOutput`s). Each
`CameraSessionConnection` connects one input to several outputs, and the session returns one `CameraController` per
connection.

## With Compose

`rememberCamera` (the equivalent of VisionCamera's `useCamera`) creates the session, configures it whenever its
parameters change, starts it while `isActive` is `true`, and returns the `CameraController`:

```kotlin
val previewOutput = rememberPreviewOutput()
val controller = rememberCamera(
    isActive = true,
    device = TargetCameraPosition.BACK,
    outputs = listOf(previewOutput),
    onError = { println("Camera error: $it") },
)
```

The `Camera` composable does the same, and also displays a preview.

## Imperatively

```kotlin
val session = VisionCamera.createCameraSession(enableMultiCam = false)
val (controller) = session.configure(
    listOf(
        CameraSessionConnection(
            input = TargetCameraPosition.BACK,
            outputs = listOf(
                CameraOutputConfiguration(previewOutput, mirrorMode = MirrorMode.AUTO),
            ),
        ),
    ),
)
session.start()
// ...
session.stop()
```

Configure the session before starting it, to avoid reconfiguration hiccups.

## Events

Listen to the session with `addOnStartedListener`, `addOnStoppedListener`, `addOnErrorListener`,
`addOnInterruptionStartedListener` and `addOnInterruptionEndedListener`. Each returns a `ListenerSubscription` to
`remove()`.

## Multi-cam

If `VisionCamera.supportsMultiCamSessions` is `true`, a session created with `enableMultiCam = true` accepts several
connections, using device combinations from `deviceFactory.supportedMultiCamDeviceCombinations`.
