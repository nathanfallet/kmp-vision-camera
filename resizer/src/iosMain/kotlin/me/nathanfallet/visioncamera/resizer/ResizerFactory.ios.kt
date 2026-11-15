package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.resizer.interop.vcr_isMetalAvailable

// Port of V5's HybridResizerFactory.swift.

internal actual fun isPlatformResizerAvailable(): Boolean = vcr_isMetalAvailable()

internal actual fun createPlatformResizerPipeline(options: ResizerOptions): ResizerPipeline =
    MetalResizerPipeline(options)
