///
/// NativeBufferHelper.cpp
/// Copyright © Marc Rousavy @ Margelo
///
/// Ported from React Native VisionCamera (MIT, see NOTICE): the fbjni registration is replaced with plain JNI
/// exports, and the CPU read of a HardwareBuffer (done by Nitro's HardwareBufferArrayBuffer in VisionCamera) is a
/// copy into a Java byte[], since Frame.getPixelBuffer() returns a ByteArray.
///

#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <jni.h>
#include <stddef.h>
#include <stdint.h>

namespace {

void throwIllegalStateException(JNIEnv* env, const char* message) {
  jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
  if (exceptionClass == nullptr) {
    // FindClass already threw (NoClassDefFoundError).
    return;
  }
  env->ThrowNew(exceptionClass, message);
}

#if __ANDROID_API__ >= 26
/// The number of bytes of one pixel of the given single-plane format, or 0 if it has none.
size_t getBytesPerPixel(uint32_t format) {
  switch (format) {
    case AHARDWAREBUFFER_FORMAT_R8_UNORM:
    case AHARDWAREBUFFER_FORMAT_S8_UINT:
      return 1;
    case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM:
    case AHARDWAREBUFFER_FORMAT_R16_UINT:
    case AHARDWAREBUFFER_FORMAT_D16_UNORM:
      return 2;
    case AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM:
      return 3;
    case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
    case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM:
    case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM:
    case AHARDWAREBUFFER_FORMAT_R16G16_UINT:
    case AHARDWAREBUFFER_FORMAT_D24_UNORM:
    case AHARDWAREBUFFER_FORMAT_D24_UNORM_S8_UINT:
    case AHARDWAREBUFFER_FORMAT_D32_FLOAT:
      return 4;
    case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT:
    case AHARDWAREBUFFER_FORMAT_R10G10B10A10_UNORM:
    // 32-bit depth + 8-bit stencil, padded to 64 bits
    case AHARDWAREBUFFER_FORMAT_D32_FLOAT_S8_UINT:
      return 8;
    default:
      // Multi-planar (YUV) formats don't have a single bytes-per-pixel value, and unknown formats can't be read.
      return 0;
  }
}
#endif

} // namespace

extern "C" {

/// Converts the given boxed android.hardware.HardwareBuffer to an AHardwareBuffer* (as a jlong), with a retain count
/// of +1 that must be released again via releaseHardwareBufferPointer.
JNIEXPORT jlong JNICALL Java_me_nathanfallet_visioncamera_NativeBufferHelper_getHardwareBufferPointer(JNIEnv* env, jclass,
                                                                                                        jobject hardwareBufferBoxed) {
#if __ANDROID_API__ >= 26
  AHardwareBuffer* hardwareBuffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferBoxed);
  AHardwareBuffer_acquire(hardwareBuffer);
  uintptr_t memoryAddress = reinterpret_cast<uintptr_t>(hardwareBuffer);
  return static_cast<jlong>(memoryAddress);
#else
  throwIllegalStateException(env, "HardwareBuffers require minSdk 26 or higher!");
  return 0;
#endif
}

/// Releases an AHardwareBuffer* previously acquired via getHardwareBufferPointer.
JNIEXPORT void JNICALL Java_me_nathanfallet_visioncamera_NativeBufferHelper_releaseHardwareBufferPointer(JNIEnv* env, jclass,
                                                                                                          jlong hardwareBufferPointer) {
#if __ANDROID_API__ >= 26
  (void)env;
  AHardwareBuffer* hardwareBuffer = reinterpret_cast<AHardwareBuffer*>(static_cast<uintptr_t>(hardwareBufferPointer));
  AHardwareBuffer_release(hardwareBuffer);
#else
  (void)hardwareBufferPointer;
  throwIllegalStateException(env, "HardwareBuffers require minSdk 26 or higher!");
#endif
}

/// Locks the given CPU-readable boxed android.hardware.HardwareBuffer for reading, and copies its bytes into a new
/// byte[] (height * stride * layers * bytes per pixel, or width for BLOB buffers).
JNIEXPORT jbyteArray JNICALL Java_me_nathanfallet_visioncamera_NativeBufferHelper_copyHardwareBufferBytes(JNIEnv* env, jclass,
                                                                                                           jobject hardwareBufferBoxed) {
#if __ANDROID_API__ >= 26
  AHardwareBuffer* hardwareBuffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBufferBoxed);
  AHardwareBuffer_Desc description;
  AHardwareBuffer_describe(hardwareBuffer, &description);
  if ((description.usage & AHARDWAREBUFFER_USAGE_CPU_READ_MASK) == 0) {
    throwIllegalStateException(env, "This HardwareBuffer is not CPU-readable!");
    return nullptr;
  }

  size_t size;
  if (description.format == AHARDWAREBUFFER_FORMAT_BLOB) {
    // BLOB buffers don't have pixels, their size is their width.
    size = static_cast<size_t>(description.width);
  } else {
    size_t bytesPerPixel = getBytesPerPixel(description.format);
    if (bytesPerPixel == 0) {
      throwIllegalStateException(env, "This HardwareBuffer's format is multi-planar or unknown, its pixels cannot be read as one buffer!");
      return nullptr;
    }
    size = static_cast<size_t>(description.height) * static_cast<size_t>(description.stride) *
           static_cast<size_t>(description.layers) * bytesPerPixel;
  }

  void* data = nullptr;
  int result = AHardwareBuffer_lock(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr, &data);
  if (result != 0 || data == nullptr) {
    throwIllegalStateException(env, "Failed to read HardwareBuffer bytes!");
    return nullptr;
  }

  jbyteArray bytes = env->NewByteArray(static_cast<jsize>(size));
  if (bytes != nullptr) {
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(size), static_cast<const jbyte*>(data));
  }
  AHardwareBuffer_unlock(hardwareBuffer, nullptr);
  // If NewByteArray failed, an OutOfMemoryError is pending.
  return bytes;
#else
  (void)hardwareBufferBoxed;
  throwIllegalStateException(env, "HardwareBuffers require minSdk 26 or higher!");
  return nullptr;
#endif
}

} // extern "C"
