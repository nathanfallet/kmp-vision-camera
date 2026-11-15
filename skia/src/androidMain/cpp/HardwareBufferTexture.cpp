///
/// HardwareBufferTexture.cpp
///
/// Imports a camera AHardwareBuffer into the current OpenGL ES context as an external texture, without copying it,
/// so that skiko (which has no `SkImages::DeferredFromAHardwareBuffer`) can wrap it as a Skia image with
/// `BackendTexture.makeGL` + `Image.adoptTextureFrom`. This is what Skia's GrAHardwareBufferUtils does on its GL
/// backend: AHardwareBuffer -> EGLClientBuffer -> EGLImage -> GL_TEXTURE_EXTERNAL_OES texture, which the GPU samples
/// in place (including YUV and implementation-defined `PRIVATE` buffers, converted by the driver).
///

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/hardware_buffer.h>
#include <jni.h>
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

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_me_nathanfallet_visioncamera_skia_HardwareBufferTexture_nativeImport(JNIEnv* env, jclass, jlong hardwareBuffer,
                                                                          jlongArray result) {
#if __ANDROID_API__ >= 26
  auto* buffer = reinterpret_cast<AHardwareBuffer*>(hardwareBuffer);
  EGLDisplay display = eglGetCurrentDisplay();
  if (display == EGL_NO_DISPLAY) {
    throwIllegalStateException(env, "No OpenGL ES context is current on this thread!");
    return JNI_FALSE;
  }
  EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(buffer);
  if (clientBuffer == nullptr) {
    throwIllegalStateException(env, "Failed to get the EGLClientBuffer of the HardwareBuffer!");
    return JNI_FALSE;
  }
  const EGLint attributes[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
  EGLImageKHR image =
      eglCreateImageKHR(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attributes);
  if (image == EGL_NO_IMAGE_KHR) {
    throwIllegalStateException(env, "Failed to create an EGLImage from the HardwareBuffer!");
    return JNI_FALSE;
  }
  GLuint texture = 0;
  glGenTextures(1, &texture);
  glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
  glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  glEGLImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES, static_cast<GLeglImageOES>(image));
  if (glGetError() != GL_NO_ERROR) {
    glDeleteTextures(1, &texture);
    eglDestroyImageKHR(display, image);
    throwIllegalStateException(env, "Failed to bind the HardwareBuffer's EGLImage to a texture!");
    return JNI_FALSE;
  }
  glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

  AHardwareBuffer_Desc description;
  AHardwareBuffer_describe(buffer, &description);
  const jlong values[] = {
      static_cast<jlong>(reinterpret_cast<intptr_t>(image)),
      static_cast<jlong>(texture),
      static_cast<jlong>(description.width),
      static_cast<jlong>(description.height),
  };
  env->SetLongArrayRegion(result, 0, 4, values);
  return JNI_TRUE;
#else
  (void) hardwareBuffer;
  (void) result;
  throwIllegalStateException(env, "HardwareBuffers are only available on API 26 or higher!");
  return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_me_nathanfallet_visioncamera_skia_HardwareBufferTexture_nativeDestroyImage(JNIEnv*, jclass, jlong display,
                                                                                jlong image) {
  eglDestroyImageKHR(reinterpret_cast<EGLDisplay>(display), reinterpret_cast<EGLImageKHR>(image));
}
