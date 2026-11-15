package me.nathanfallet.visioncamera.resizer

import android.os.Build

// Port of V5's HybridResizerFactory.cpp.

internal actual fun isPlatformResizerAvailable(): Boolean =
    // Frames expose their AHardwareBuffer from Android 9 (API 28) on.
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ResizerNative.isSupported()

internal actual fun createPlatformResizerPipeline(options: ResizerOptions): ResizerPipeline =
    VulkanResizerPipeline(options)
