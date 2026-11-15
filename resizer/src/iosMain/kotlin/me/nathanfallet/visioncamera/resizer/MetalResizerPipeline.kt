package me.nathanfallet.visioncamera.resizer

import cnames.structs.__CVBuffer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import me.nathanfallet.visioncamera.VisionCameraException
import me.nathanfallet.visioncamera.resizer.interop.VCRMetalResizerPipeline
import me.nathanfallet.visioncamera.resizer.interop.VCRMetalResizerPipelineFactory
import platform.Foundation.NSData
import platform.Foundation.dataWithBytesNoCopy
import platform.posix.memcpy

/**
 * The iOS [ResizerPipeline]: V5's Metal `MetalResizerPipeline`, ported to Objective-C in
 * src/iosMain/objc/VisionCameraResizerNative.m.
 */
@OptIn(BetaInteropApi::class)
internal class MetalResizerPipeline(
    override val options: ResizerOptions,
) : ResizerPipeline {
    private val pipeline: VCRMetalResizerPipeline = memScoped {
        val errorMessage = alloc<ObjCObjectVar<String?>>()
        VCRMetalResizerPipelineFactory.createPipelineWithShaderSource(
            shaderSource = RESIZER_KERNELS_SOURCE,
            functionName = options.dataType.kernelName,
            outputWidth = options.width.convert(),
            outputHeight = options.height.convert(),
            channelOrder = options.channelOrder.shaderOrdinal.convert(),
            pixelLayout = options.pixelLayout.shaderOrdinal.convert(),
            channelCount = options.channelOrder.channelsPerPixel.convert(),
            scaleMode = options.scaleMode.shaderOrdinal.convert(),
            outputByteCount = options.outputByteCount.convert(),
            errorMessage = errorMessage.ptr,
        ) ?: throw VisionCameraException(errorMessage.value ?: "Failed to create the Metal resizer pipeline.")
    }

    override fun run(nativeBufferPointer: Long, rotationDegrees: Int, isMirrored: Boolean): ResizerPipeline.OutputView {
        val pixelBuffer = nativeBufferPointer.toCPointer<__CVBuffer>()
            ?: throw VisionCameraException("Frame NativeBuffer pointer (CVPixelBufferRef) is null!")
        memScoped {
            val errorMessage = alloc<ObjCObjectVar<String?>>()
            if (!pipeline.runWithPixelBuffer(pixelBuffer, rotationDegrees, isMirrored, errorMessage.ptr)) {
                throw VisionCameraException(errorMessage.value ?: "The Metal resize pipeline failed.")
            }
        }
        return OutputView(pipeline)
    }

    override fun destroy() {
        pipeline.invalidate()
    }

    /** A view over the shared Metal output buffer (V5's `MetalBufferView`). */
    class OutputView(private val pipeline: VCRMetalResizerPipeline) : ResizerPipeline.OutputView {
        private val byteCount: Int = pipeline.outputByteCount.convert()

        private val contents: COpaquePointer
            get() = pipeline.outputContents ?: throw VisionCameraException("This Resizer has already been disposed!")

        override fun copyBytes(): ByteArray {
            val bytes = ByteArray(byteCount)
            bytes.usePinned { memcpy(it.addressOf(0), contents, byteCount.convert()) }
            return bytes
        }

        /** Wraps the shared output buffer in an [NSData], without copying. */
        fun getData(): NSData = NSData.dataWithBytesNoCopy(contents, byteCount.convert(), freeWhenDone = false)

        override fun release() = Unit
    }

    private companion object {
        /** The Metal kernel of ResizerKernels.metal writing this data type. */
        val DataType.kernelName: String
            get() = when (this) {
                DataType.INT8 -> "resize_int8"
                DataType.UINT8 -> "resize_uint8"
                DataType.FLOAT16 -> "resize_float16"
                DataType.FLOAT32 -> "resize_float32"
            }
    }
}
