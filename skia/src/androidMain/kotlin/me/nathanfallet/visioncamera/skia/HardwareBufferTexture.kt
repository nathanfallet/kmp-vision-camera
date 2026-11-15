package me.nathanfallet.visioncamera.skia

/**
 * The JNI bindings of libVisionCameraSkia.so (src/androidMain/cpp/HardwareBufferTexture.cpp).
 */
internal object HardwareBufferTexture {
    init {
        System.loadLibrary("VisionCameraSkia")
    }

    /**
     * Imports the `AHardwareBuffer*` [hardwareBuffer] into the current OpenGL ES context as a `GL_TEXTURE_EXTERNAL_OES`
     * texture, and writes its EGLImage, texture name, width and height into [result].
     */
    @JvmStatic
    external fun nativeImport(hardwareBuffer: Long, result: LongArray): Boolean

    /** Destroys an EGLImage returned by [nativeImport] (the texture keeps the buffer alive while it exists). */
    @JvmStatic
    external fun nativeDestroyImage(display: Long, image: Long)
}
