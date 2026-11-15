#pragma once

// Native helpers of the skia module's iOS implementation, bound to Kotlin by cinterop (VisionCameraSkia.def), and
// compiled into libVisionCameraSkia.a (VisionCameraSkia.mm) by the skia module's buildIosNativeLibrary* tasks.
//
// They wrap a camera CVPixelBuffer as a Skia GPU image without copying it (the equivalent of React Native Skia's
// `Skia.Image.MakeImageFromNativeBuffer`, which skiko does not have): the pixel buffer's planes become Metal textures
// through a CVMetalTextureCache, and Skia samples them in place (BGRA directly, YUV with an in-shader conversion).
// The Skia symbols are resolved at link time from skiko's libskia.a, so the vendored Skia headers (skia-headers/)
// must match skiko's Skia version.

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/** An opaque Metal context of one rendering thread: a MTLDevice, a MTLCommandQueue and a CVMetalTextureCache. */
typedef struct VCSkiaMetalContext VCSkiaMetalContext;

/** Creates a Metal context on the system default device, or returns NULL if Metal is not available. */
VCSkiaMetalContext *_Nullable vcskia_metal_context_create(void);

/** The `id<MTLDevice>` of the context, unretained (to create a skiko DirectContext with). */
void *_Nonnull vcskia_metal_context_get_device(VCSkiaMetalContext *_Nonnull context);

/** The `id<MTLCommandQueue>` of the context, unretained (to create a skiko DirectContext with). */
void *_Nonnull vcskia_metal_context_get_queue(VCSkiaMetalContext *_Nonnull context);

/** Destroys the context. Images made with it keep their textures alive until Skia releases them. */
void vcskia_metal_context_destroy(VCSkiaMetalContext *_Nullable context);

/**
 * Wraps the given `CVPixelBufferRef` (32-bit BGRA, or 8/10-bit bi-planar YUV) as an `SkImage*` with a +1 reference,
 * sampled in place on the GPU by the given `GrDirectContext*` (which must use the context's MTLDevice).
 *
 * Returns NULL and writes a message into errorMessage on failure.
 */
void *_Nullable vcskia_make_image_from_pixel_buffer(VCSkiaMetalContext *_Nonnull context,
                                                    void *_Nonnull directContext,
                                                    void *_Nonnull pixelBuffer,
                                                    char *_Nonnull errorMessage,
                                                    size_t errorMessageLength);

#ifdef __cplusplus
}
#endif
