---
title: Lifecycle
parent: Guides
nav_order: 3
---

# Lifecycle

A session runs while it is started **and** the app is in the foreground:

- On Android, the session follows the app's lifecycle (`ProcessLifecycleOwner`): the camera is closed when the app
  goes to the background, and reopened when it comes back, as long as the session is started.
- On iOS, the system interrupts the session in the background
  (`InterruptionReason.VIDEO_DEVICE_NOT_AVAILABLE_IN_BACKGROUND`) and resumes it automatically.

With Compose, `isActive` starts and stops the session. Set it to `false` when the camera screen is not visible (for
example behind another screen) to save battery:

```kotlin
val isFocused = /* e.g. from your navigation */ true
Camera(device = TargetCameraPosition.BACK, isActive = isFocused)
```

Interruptions (another app using the camera, system pressure, phone calls…) are reported through the session's
interruption listeners, or `onInterruptionStarted`/`onInterruptionEnded` in Compose.
