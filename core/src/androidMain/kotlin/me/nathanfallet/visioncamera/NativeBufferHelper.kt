package me.nathanfallet.visioncamera

import android.hardware.HardwareBuffer

/**
 * The JNI bridge to `libVisionCamera.so` (src/androidMain/cpp), to unwrap [HardwareBuffer]s as `AHardwareBuffer*`.
 */
internal object NativeBufferHelper {
    init {
        System.loadLibrary("VisionCamera")
    }

    /**
     * Converts the given [HardwareBuffer] to a pointer address ([Long]).
     *
     * This acquires a reference count of +1, which must be released
     * later again via [releaseHardwareBufferPointer].
     */
    @JvmStatic
    external fun getHardwareBufferPointer(hardwareBuffer: HardwareBuffer): Long

    /**
     * Releases a HardwareBuffer which is passed here as
     * a pointer address ([Long]), which was previously acquired
     * via [getHardwareBufferPointer].
     *
     * This must be called exactly once.
     * Never call it and you have a leak, call it twice
     * and you have a double free.
     */
    @JvmStatic
    external fun releaseHardwareBufferPointer(hardwareBufferPointer: Long)

    /**
     * Locks the given CPU-readable [HardwareBuffer] for reading, and copies its pixel data into a new [ByteArray].
     */
    @JvmStatic
    external fun copyHardwareBufferBytes(hardwareBuffer: HardwareBuffer): ByteArray
}
