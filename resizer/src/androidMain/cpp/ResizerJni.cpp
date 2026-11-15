///
/// ResizerJni.cpp
/// VisionCamera
///
/// The JNI bridge of libVisionCameraResizer.so, bound to Kotlin by ResizerNative.kt. It replaces V5's Nitro
/// HybridResizerFactory, HybridResizer and HybridGPUFrame: the Kotlin classes hold the native objects as handles
/// (jlong), and C++ exceptions are rethrown as java.lang.IllegalStateException.
///

#include "vulkan/VulkanResizerPipeline.hpp"

#include <android/hardware_buffer.h>
#include <jni.h>

#include <cstdint>
#include <exception>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>

using visioncamera::resizer::ChannelOrder;
using visioncamera::resizer::DataType;
using visioncamera::resizer::PixelLayout;
using visioncamera::resizer::ResizerOptions;
using visioncamera::resizer::ScaleMode;
using visioncamera::resizer::vulkan::VulkanBufferView;
using visioncamera::resizer::vulkan::VulkanResizerPipeline;

namespace {

/**
 * One live output view of a pipeline (V5's HybridGPUFrame). Deleting it releases the pipeline's reusable output buffer.
 */
struct GPUFrameHandle final {
  std::shared_ptr<VulkanBufferView> bufferView;
};

void throwIllegalStateException(JNIEnv* env, const char* message) {
  jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
  if (exceptionClass == nullptr) {
    // FindClass already threw (NoClassDefFoundError).
    return;
  }
  env->ThrowNew(exceptionClass, message);
}

/**
 * Runs the given function, and rethrows any C++ exception as a Java IllegalStateException (returning `fallback`).
 */
template <typename Result, typename Function>
Result rethrowAsJava(JNIEnv* env, Result fallback, Function&& function) {
  try {
    return function();
  } catch (const std::exception& error) {
    throwIllegalStateException(env, error.what());
  } catch (...) {
    throwIllegalStateException(env, "Unknown native error in the Vulkan resizer pipeline.");
  }
  return fallback;
}

template <typename Enum>
Enum enumFromOrdinal(jint ordinal, jint count, const char* name) {
  if (ordinal < 0 || ordinal >= count) [[unlikely]] {
    throw std::runtime_error(std::string("Unsupported Resizer ") + name + " ordinal: " + std::to_string(ordinal));
  }
  return static_cast<Enum>(ordinal);
}

VulkanResizerPipeline* pipelineFromHandle(jlong handle) {
  auto* pipeline = reinterpret_cast<VulkanResizerPipeline*>(static_cast<uintptr_t>(handle));
  if (pipeline == nullptr) [[unlikely]] {
    throw std::runtime_error("This Resizer has already been disposed!");
  }
  return pipeline;
}

VulkanBufferView& bufferViewFromHandle(jlong handle) {
  auto* frame = reinterpret_cast<GPUFrameHandle*>(static_cast<uintptr_t>(handle));
  if (frame == nullptr || frame->bufferView == nullptr) [[unlikely]] {
    throw std::runtime_error("This GPUFrame has already been disposed!");
  }
  return *frame->bufferView;
}

} // namespace

extern "C" {

/// Whether the Vulkan device and the AHardwareBuffer extensions needed by the pipeline are available (V5's
/// HybridResizerFactory::isAvailable).
JNIEXPORT jboolean JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_isSupported(JNIEnv*, jclass) {
  return VulkanResizerPipeline::isSupported() ? JNI_TRUE : JNI_FALSE;
}

/// Creates a VulkanResizerPipeline for the given options (Kotlin enum ordinals), and returns its handle.
JNIEXPORT jlong JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_createPipeline(JNIEnv* env, jclass, jint width, jint height,
                                                                                               jint channelOrder, jint dataType, jint scaleMode,
                                                                                               jint pixelLayout) {
  return rethrowAsJava(env, jlong{0}, [&]() {
    const ResizerOptions options{
        .width = static_cast<double>(width),
        .height = static_cast<double>(height),
        .channelOrder = enumFromOrdinal<ChannelOrder>(channelOrder, 2, "ChannelOrder"),
        .dataType = enumFromOrdinal<DataType>(dataType, 4, "DataType"),
        .scaleMode = enumFromOrdinal<ScaleMode>(scaleMode, 3, "ScaleMode"),
        .pixelLayout = enumFromOrdinal<PixelLayout>(pixelLayout, 2, "PixelLayout"),
    };
    auto* pipeline = new VulkanResizerPipeline(options);
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(pipeline));
  });
}

/// Destroys the pipeline of the given handle. All of its output views must have been released before.
JNIEXPORT void JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_destroyPipeline(JNIEnv* env, jclass, jlong pipelineHandle) {
  rethrowAsJava(env, 0, [&]() {
    VulkanResizerPipeline* pipeline = pipelineFromHandle(pipelineHandle);
    if (pipeline->hasActiveOutputView()) [[unlikely]] {
      throw std::runtime_error("Previous GPUFrame is still active. Dispose it before disposing the Resizer.");
    }
    delete pipeline;
    return 0;
  });
}

/// Resizes the given AHardwareBuffer* (borrowed, the caller keeps its reference until this returns) with the pipeline,
/// and returns the handle of a view over the pipeline's output buffer.
JNIEXPORT jlong JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_run(JNIEnv* env, jclass, jlong pipelineHandle,
                                                                                    jlong hardwareBufferPointer, jint rotationDegrees,
                                                                                    jboolean isMirrored) {
  return rethrowAsJava(env, jlong{0}, [&]() {
    VulkanResizerPipeline* pipeline = pipelineFromHandle(pipelineHandle);
    auto* hardwareBuffer = reinterpret_cast<AHardwareBuffer*>(static_cast<uintptr_t>(hardwareBufferPointer));
    if (hardwareBuffer == nullptr) [[unlikely]] {
      throw std::runtime_error("Frame NativeBuffer pointer (AHardwareBuffer*) is null!");
    }
    std::shared_ptr<VulkanBufferView> bufferView = pipeline->run(hardwareBuffer, rotationDegrees, isMirrored == JNI_TRUE);
    auto* frame = new GPUFrameHandle{std::move(bufferView)};
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(frame));
  });
}

/// Releases the output view of the given handle, so the pipeline can run again.
JNIEXPORT void JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_releaseFrame(JNIEnv*, jclass, jlong frameHandle) {
  delete reinterpret_cast<GPUFrameHandle*>(static_cast<uintptr_t>(frameHandle));
}

/// Wraps the mapped Vulkan output memory of the given view in a direct java.nio.ByteBuffer, without copying.
JNIEXPORT jobject JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_getFrameByteBuffer(JNIEnv* env, jclass, jlong frameHandle) {
  return rethrowAsJava(env, static_cast<jobject>(nullptr), [&]() {
    VulkanBufferView& bufferView = bufferViewFromHandle(frameHandle);
    return env->NewDirectByteBuffer(bufferView.getData(), static_cast<jlong>(bufferView.getByteCount()));
  });
}

/// Copies the mapped Vulkan output memory of the given view into a new byte[].
JNIEXPORT jbyteArray JNICALL Java_me_nathanfallet_visioncamera_resizer_ResizerNative_copyFrameBytes(JNIEnv* env, jclass, jlong frameHandle) {
  return rethrowAsJava(env, static_cast<jbyteArray>(nullptr), [&]() {
    VulkanBufferView& bufferView = bufferViewFromHandle(frameHandle);
    const auto size = static_cast<jsize>(bufferView.getByteCount());
    jbyteArray bytes = env->NewByteArray(size);
    if (bytes != nullptr) {
      env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte*>(bufferView.getData()));
    }
    // If NewByteArray failed, an OutOfMemoryError is pending.
    return bytes;
  });
}

} // extern "C"
