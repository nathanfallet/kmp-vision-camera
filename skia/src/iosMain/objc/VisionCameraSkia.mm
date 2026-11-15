#import "VisionCameraSkia.h"

#import <CoreVideo/CoreVideo.h>
#import <Metal/Metal.h>

#include <cstdio>

#include "include/core/SkColorSpace.h"
#include "include/core/SkImage.h"
#include "include/core/SkYUVAInfo.h"
#include "include/gpu/ganesh/GrBackendSurface.h"
#include "include/gpu/ganesh/GrDirectContext.h"
#include "include/gpu/ganesh/GrYUVABackendTextures.h"
#include "include/gpu/ganesh/SkImageGanesh.h"
#include "include/gpu/ganesh/mtl/GrMtlBackendSurface.h"
#include "include/gpu/ganesh/mtl/GrMtlTypes.h"

struct VCSkiaMetalContext {
  id<MTLDevice> device;
  id<MTLCommandQueue> queue;
  CVMetalTextureCacheRef textureCache;
};

namespace {

/// The CVMetalTextures backing an SkImage, released by Skia once it is done with the image.
struct TexturesReleaseContext {
  CVMetalTextureRef textures[SkYUVAInfo::kMaxPlanes] = {};
};

void releaseTextures(void *releaseContext) {
  auto *context = static_cast<TexturesReleaseContext *>(releaseContext);
  for (CVMetalTextureRef texture : context->textures) {
    if (texture != nullptr) CFRelease(texture);
  }
  delete context;
}

void writeError(char *errorMessage, size_t errorMessageLength, const char *message) {
  std::snprintf(errorMessage, errorMessageLength, "%s", message);
}

/// Wraps one plane of the pixel buffer as a Metal texture (stored in releaseContext) and a GrBackendTexture.
bool makePlaneTexture(VCSkiaMetalContext *context, CVPixelBufferRef pixelBuffer, size_t planeIndex,
                      MTLPixelFormat pixelFormat, TexturesReleaseContext *releaseContext,
                      GrBackendTexture *backendTexture) {
  bool isPlanar = CVPixelBufferIsPlanar(pixelBuffer);
  size_t width = isPlanar ? CVPixelBufferGetWidthOfPlane(pixelBuffer, planeIndex) : CVPixelBufferGetWidth(pixelBuffer);
  size_t height =
      isPlanar ? CVPixelBufferGetHeightOfPlane(pixelBuffer, planeIndex) : CVPixelBufferGetHeight(pixelBuffer);
  CVMetalTextureRef texture = nullptr;
  CVReturn result = CVMetalTextureCacheCreateTextureFromImage(
      kCFAllocatorDefault, context->textureCache, pixelBuffer, nullptr, pixelFormat, width, height, planeIndex,
      &texture);
  if (result != kCVReturnSuccess || texture == nullptr) return false;
  releaseContext->textures[planeIndex] = texture;
  id<MTLTexture> metalTexture = CVMetalTextureGetTexture(texture);
  if (metalTexture == nil) return false;
  GrMtlTextureInfo textureInfo;
  textureInfo.fTexture.retain((__bridge const void *)metalTexture);
  *backendTexture = GrBackendTextures::MakeMtl(static_cast<int>(width), static_cast<int>(height),
                                               skgpu::Mipmapped::kNo, textureInfo);
  return backendTexture->isValid();
}

/// The SkYUVColorSpace of the pixel buffer, from its YCbCr matrix attachment (BT.709 if it has none).
SkYUVColorSpace getYUVColorSpace(CVPixelBufferRef pixelBuffer, bool isFullRange, bool is10Bit) {
  CFTypeRef matrix = CVBufferCopyAttachment(pixelBuffer, kCVImageBufferYCbCrMatrixKey, nullptr);
  bool is601 = matrix != nullptr && CFEqual(matrix, kCVImageBufferYCbCrMatrix_ITU_R_601_4);
  bool is2020 = matrix != nullptr && CFEqual(matrix, kCVImageBufferYCbCrMatrix_ITU_R_2020);
  if (matrix != nullptr) CFRelease(matrix);
  if (is601) return isFullRange ? kJPEG_Full_SkYUVColorSpace : kRec601_Limited_SkYUVColorSpace;
  if (is2020) {
    // 10-bit samples are stored in the high bits of 16-bit values, so they are normalized like 16-bit ones.
    if (is10Bit) return isFullRange ? kBT2020_16bit_Full_SkYUVColorSpace : kBT2020_16bit_Limited_SkYUVColorSpace;
    return isFullRange ? kBT2020_8bit_Full_SkYUVColorSpace : kBT2020_8bit_Limited_SkYUVColorSpace;
  }
  return isFullRange ? kRec709_Full_SkYUVColorSpace : kRec709_Limited_SkYUVColorSpace;
}

sk_sp<SkImage> makeBGRAImage(VCSkiaMetalContext *context, GrDirectContext *directContext,
                             CVPixelBufferRef pixelBuffer, char *errorMessage, size_t errorMessageLength) {
  auto *releaseContext = new TexturesReleaseContext();
  GrBackendTexture texture;
  if (!makePlaneTexture(context, pixelBuffer, 0, MTLPixelFormatBGRA8Unorm, releaseContext, &texture)) {
    releaseTextures(releaseContext);
    writeError(errorMessage, errorMessageLength, "Failed to create a Metal texture from the BGRA CVPixelBuffer!");
    return nullptr;
  }
  // Skia calls releaseTextures even if it fails to create the image.
  return SkImages::BorrowTextureFrom(directContext, texture, kTopLeft_GrSurfaceOrigin, kBGRA_8888_SkColorType,
                                     kOpaque_SkAlphaType, nullptr, releaseTextures, releaseContext);
}

sk_sp<SkImage> makeYUVImage(VCSkiaMetalContext *context, GrDirectContext *directContext,
                            CVPixelBufferRef pixelBuffer, bool isFullRange, bool is10Bit, char *errorMessage,
                            size_t errorMessageLength) {
  auto *releaseContext = new TexturesReleaseContext();
  GrBackendTexture textures[SkYUVAInfo::kMaxPlanes];
  MTLPixelFormat yFormat = is10Bit ? MTLPixelFormatR16Unorm : MTLPixelFormatR8Unorm;
  MTLPixelFormat uvFormat = is10Bit ? MTLPixelFormatRG16Unorm : MTLPixelFormatRG8Unorm;
  if (!makePlaneTexture(context, pixelBuffer, 0, yFormat, releaseContext, &textures[0]) ||
      !makePlaneTexture(context, pixelBuffer, 1, uvFormat, releaseContext, &textures[1])) {
    releaseTextures(releaseContext);
    writeError(errorMessage, errorMessageLength, "Failed to create Metal textures from the YUV CVPixelBuffer!");
    return nullptr;
  }
  SkISize size = SkISize::Make(static_cast<int>(CVPixelBufferGetWidth(pixelBuffer)),
                               static_cast<int>(CVPixelBufferGetHeight(pixelBuffer)));
  SkYUVAInfo yuvaInfo(size, SkYUVAInfo::PlaneConfig::kY_UV, SkYUVAInfo::Subsampling::k420,
                      getYUVColorSpace(pixelBuffer, isFullRange, is10Bit));
  GrYUVABackendTextures yuvaTextures(yuvaInfo, textures, kTopLeft_GrSurfaceOrigin);
  // Skia calls releaseTextures even if it fails to create the image.
  return SkImages::TextureFromYUVATextures(directContext, yuvaTextures, nullptr, releaseTextures, releaseContext);
}

} // namespace

VCSkiaMetalContext *vcskia_metal_context_create(void) {
  id<MTLDevice> device = MTLCreateSystemDefaultDevice();
  if (device == nil) return nullptr;
  id<MTLCommandQueue> queue = [device newCommandQueue];
  if (queue == nil) return nullptr;
  CVMetalTextureCacheRef textureCache = nullptr;
  if (CVMetalTextureCacheCreate(kCFAllocatorDefault, nullptr, device, nullptr, &textureCache) != kCVReturnSuccess) {
    return nullptr;
  }
  auto *context = new VCSkiaMetalContext();
  context->device = device;
  context->queue = queue;
  context->textureCache = textureCache;
  return context;
}

void *vcskia_metal_context_get_device(VCSkiaMetalContext *context) {
  return (__bridge void *)context->device;
}

void *vcskia_metal_context_get_queue(VCSkiaMetalContext *context) {
  return (__bridge void *)context->queue;
}

void vcskia_metal_context_destroy(VCSkiaMetalContext *context) {
  if (context == nullptr) return;
  CVMetalTextureCacheFlush(context->textureCache, 0);
  CFRelease(context->textureCache);
  delete context;
}

void *vcskia_make_image_from_pixel_buffer(VCSkiaMetalContext *context, void *directContext, void *pixelBuffer,
                                          char *errorMessage, size_t errorMessageLength) {
  auto *grContext = static_cast<GrDirectContext *>(directContext);
  auto buffer = static_cast<CVPixelBufferRef>(pixelBuffer);
  OSType format = CVPixelBufferGetPixelFormatType(buffer);
  sk_sp<SkImage> image;
  switch (format) {
    case kCVPixelFormatType_32BGRA:
      image = makeBGRAImage(context, grContext, buffer, errorMessage, errorMessageLength);
      break;
    case kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange:
    case kCVPixelFormatType_420YpCbCr8BiPlanarFullRange:
    case kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange:
    case kCVPixelFormatType_420YpCbCr10BiPlanarFullRange: {
      bool isFullRange = format == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange ||
                         format == kCVPixelFormatType_420YpCbCr10BiPlanarFullRange;
      bool is10Bit = format == kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange ||
                     format == kCVPixelFormatType_420YpCbCr10BiPlanarFullRange;
      image = makeYUVImage(context, grContext, buffer, isFullRange, is10Bit, errorMessage, errorMessageLength);
      break;
    }
    default:
      std::snprintf(errorMessage, errorMessageLength,
                    "CVPixelBuffer pixel format %c%c%c%c is not supported by Skia! "
                    "Use the 'yuv' or 'rgb' pixel format.",
                    static_cast<char>(format >> 24), static_cast<char>(format >> 16), static_cast<char>(format >> 8),
                    static_cast<char>(format));
      return nullptr;
  }
  if (image == nullptr) {
    if (errorMessage[0] == '\0') {
      writeError(errorMessage, errorMessageLength, "Skia failed to wrap the CVPixelBuffer's textures in an image!");
    }
    return nullptr;
  }
  CVMetalTextureCacheFlush(context->textureCache, 0);
  return image.release();
}
