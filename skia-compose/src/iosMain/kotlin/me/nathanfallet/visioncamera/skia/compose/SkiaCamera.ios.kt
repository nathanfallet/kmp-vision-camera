package me.nathanfallet.visioncamera.skia.compose

import me.nathanfallet.visioncamera.TargetVideoPixelFormat

/** iOS Frames are sampled as bi-planar YUV Metal textures, converted to RGB in Skia's shaders. */
internal actual val defaultSkiaPixelFormat: TargetVideoPixelFormat = TargetVideoPixelFormat.YUV
