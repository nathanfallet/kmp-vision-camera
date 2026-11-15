package me.nathanfallet.visioncamera.resizer

import java.nio.ByteBuffer

/**
 * The JNI bridge to `libVisionCameraResizer.so` (src/androidMain/cpp/ResizerJni.cpp), V5's Vulkan resizer pipeline.
 *
 * Native errors are thrown as [IllegalStateException]s.
 */
internal object ResizerNative {
    init {
        System.loadLibrary("VisionCameraResizer")
    }

    /** Whether a Vulkan 1.1 device with the `AHardwareBuffer` import extensions is available. */
    @JvmStatic
    external fun isSupported(): Boolean

    /**
     * Creates a Vulkan pipeline for the given options (enum ordinals), and returns its handle, to [destroyPipeline].
     */
    @JvmStatic
    @Suppress("LongParameterList")
    external fun createPipeline(
        width: Int,
        height: Int,
        channelOrder: Int,
        dataType: Int,
        scaleMode: Int,
        pixelLayout: Int,
    ): Long

    /** Destroys the pipeline of the given handle, whose frames must all have been released. */
    @JvmStatic
    external fun destroyPipeline(pipelineHandle: Long)

    /**
     * Resizes the given `AHardwareBuffer*` (borrowed during the call) with the pipeline, and returns the handle of a
     * view over its output buffer, to [releaseFrame].
     */
    @JvmStatic
    external fun run(pipelineHandle: Long, hardwareBufferPointer: Long, rotationDegrees: Int, isMirrored: Boolean): Long

    /** Releases the output view of the given handle, so the pipeline can run again. */
    @JvmStatic
    external fun releaseFrame(frameHandle: Long)

    /** Wraps the output buffer of the given view in a direct [ByteBuffer], without copying. */
    @JvmStatic
    external fun getFrameByteBuffer(frameHandle: Long): ByteBuffer

    /** Copies the output buffer of the given view into a new [ByteArray]. */
    @JvmStatic
    external fun copyFrameBytes(frameHandle: Long): ByteArray
}
