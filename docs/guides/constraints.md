---
title: Constraints
parent: Guides
nav_order: 4
---

# Constraints

Constraints describe your _intent_: the session negotiates them with the device and the connected outputs to find
the closest supported configuration. They are ranked by priority, the first one being the most important.

```kotlin
val constraints = listOf(
    ResolutionBiasConstraint(photoOutput),          // Photo resolution matters most
    PhotoHDRConstraint(true),
    VideoDynamicRangeConstraint(CommonDynamicRanges.ANY_HDR),
    FPSConstraint(60.0),
)
```

Available constraints: `FPSConstraint`, `VideoStabilizationModeConstraint`, `PreviewStabilizationModeConstraint`,
`ResolutionBiasConstraint`, `VideoDynamicRangeConstraint`, `PhotoHDRConstraint`, `PixelFormatConstraint` and
`BinnedConstraint`.

When the full combination is not supported, the lowest priority constraints are downgraded (e.g. cinematic
stabilization to standard, HDR to SDR) or dropped. FPS is resolved last, against the ranges supported by the
resolved configuration.

To know what was selected, use `onSessionConfigSelected`, or resolve constraints upfront:

```kotlin
val config = VisionCamera.resolveConstraints(device, outputConfigurations, constraints)
println(config.selectedFPS)
```
