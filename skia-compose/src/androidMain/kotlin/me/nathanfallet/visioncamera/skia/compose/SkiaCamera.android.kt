package me.nathanfallet.visioncamera.skia.compose

import me.nathanfallet.visioncamera.TargetVideoPixelFormat

/** Android Frames are imported as external OpenGL ES textures, which sample any native format (even `PRIVATE`). */
internal actual val defaultSkiaPixelFormat: TargetVideoPixelFormat = TargetVideoPixelFormat.NATIVE
