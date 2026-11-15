// Port of React Native VisionCamera's resizer package (MIT, see NOTICE): ios/Metal/MetalResizerPipeline.swift,
// MetalResizerShaderLibrary.swift, MetalResizerInputTextures.swift and MetalReusableBuffer.swift. V5 loads a
// precompiled metallib from a resource bundle, here ResizerKernels.metal is compiled from source once per process, so
// that apps need no bundle.

#import "VisionCameraResizerNative.h"

#import <Metal/Metal.h>

/** The per-dispatch parameters shared with the Metal kernel (`ResizeUniforms` in ResizerKernels.metal). */
typedef struct {
  uint32_t outputWidth;
  uint32_t outputHeight;
  int32_t rotationDegrees;
  uint32_t isMirrored;
} VCRResizeUniforms;

static BOOL VCRFail(NSString **errorMessage, NSString *message) {
  if (errorMessage != NULL) {
    *errorMessage = message;
  }
  return NO;
}

/** Compiles the shader source once per process and device, like V5's precompiled library. */
static id<MTLLibrary> VCRLoadLibrary(id<MTLDevice> device, NSString *source, NSString **errorMessage) {
  static id<MTLLibrary> cachedLibrary = nil;
  static NSObject *lock = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    lock = [NSObject new];
  });
  @synchronized(lock) {
    if (cachedLibrary != nil && cachedLibrary.device == device) {
      return cachedLibrary;
    }
    NSError *compileError = nil;
    id<MTLLibrary> library = [device newLibraryWithSource:source options:[MTLCompileOptions new] error:&compileError];
    if (library == nil) {
      VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to compile the Metal resizer shaders: %@",
                                                       compileError.localizedDescription]);
      return nil;
    }
    cachedLibrary = library;
    return library;
  }
}

/** Wraps one plane of the pixel buffer into a Metal texture, through the texture cache (+1, release it). */
static CVMetalTextureRef VCRCreatePlaneTexture(CVMetalTextureCacheRef textureCache, CVPixelBufferRef pixelBuffer,
                                               MTLPixelFormat pixelFormat, size_t planeIndex,
                                               NSString **errorMessage) {
  size_t width = CVPixelBufferGetWidthOfPlane(pixelBuffer, planeIndex);
  size_t height = CVPixelBufferGetHeightOfPlane(pixelBuffer, planeIndex);
  CVMetalTextureRef texture = NULL;
  CVReturn status = CVMetalTextureCacheCreateTextureFromImage(kCFAllocatorDefault, textureCache, pixelBuffer, NULL,
                                                              pixelFormat, width, height, planeIndex, &texture);
  if (status != kCVReturnSuccess || texture == NULL) {
    VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to create Metal texture for plane %zu (status %d).",
                                                     planeIndex, status]);
    return NULL;
  }
  if (CVMetalTextureGetTexture(texture) == nil) {
    CFRelease(texture);
    VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to get Metal texture for plane %zu.", planeIndex]);
    return NULL;
  }
  return texture;
}

BOOL vcr_isMetalAvailable(void) {
  return MTLCreateSystemDefaultDevice() != nil;
}

@implementation VCRMetalResizerPipeline {
  uint32_t _outputWidth;
  uint32_t _outputHeight;
  id<MTLCommandQueue> _commandQueue;
  CVMetalTextureCacheRef _textureCache;
  id<MTLComputePipelineState> _pipelineState;
  MTLSize _threadsPerThreadgroup;
  id<MTLBuffer> _outputBuffer;
}

- (nullable instancetype)initWithShaderSource:(NSString *)shaderSource
                                 functionName:(NSString *)functionName
                                  outputWidth:(uint32_t)outputWidth
                                 outputHeight:(uint32_t)outputHeight
                                 channelOrder:(uint32_t)channelOrder
                                  pixelLayout:(uint32_t)pixelLayout
                                 channelCount:(uint32_t)channelCount
                                    scaleMode:(uint32_t)scaleMode
                              outputByteCount:(NSUInteger)outputByteCount
                                 errorMessage:(NSString **)errorMessage {
  self = [super init];
  if (self == nil) {
    return nil;
  }
  _outputWidth = outputWidth;
  _outputHeight = outputHeight;
  _outputByteCount = outputByteCount;

  id<MTLDevice> device = MTLCreateSystemDefaultDevice();
  if (device == nil) {
    VCRFail(errorMessage, @"Failed to initialize Metal - no MTLDevice is available.");
    return nil;
  }
  _commandQueue = [device newCommandQueue];
  if (_commandQueue == nil) {
    VCRFail(errorMessage, @"Failed to initialize Metal command queue.");
    return nil;
  }

  // Bake the fixed output layout into the Metal function so only per-frame transform inputs stay dynamic.
  id<MTLLibrary> library = VCRLoadLibrary(device, shaderSource, errorMessage);
  if (library == nil) {
    return nil;
  }
  MTLFunctionConstantValues *constantValues = [MTLFunctionConstantValues new];
  [constantValues setConstantValue:&channelOrder type:MTLDataTypeUInt atIndex:0];
  [constantValues setConstantValue:&pixelLayout type:MTLDataTypeUInt atIndex:1];
  [constantValues setConstantValue:&channelCount type:MTLDataTypeUInt atIndex:2];
  [constantValues setConstantValue:&scaleMode type:MTLDataTypeUInt atIndex:3];
  NSError *functionError = nil;
  id<MTLFunction> function = [library newFunctionWithName:functionName
                                           constantValues:constantValues
                                                    error:&functionError];
  if (function == nil) {
    VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to specialize Metal function `%@`: %@", functionName,
                                                     functionError.localizedDescription]);
    return nil;
  }
  NSError *pipelineError = nil;
  _pipelineState = [device newComputePipelineStateWithFunction:function error:&pipelineError];
  if (_pipelineState == nil) {
    VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to create Metal resize pipeline (%@): %@", functionName,
                                                     pipelineError.localizedDescription]);
    return nil;
  }

  // Choose a threadgroup size that matches the device's compute limits.
  NSUInteger threadgroupWidth = MIN(_pipelineState.threadExecutionWidth, (NSUInteger)16);
  NSUInteger threadgroupHeight =
      MAX((NSUInteger)1, MIN((NSUInteger)16, _pipelineState.maxTotalThreadsPerThreadgroup / MAX(threadgroupWidth, 1)));
  _threadsPerThreadgroup = MTLSizeMake(threadgroupWidth, threadgroupHeight, 1);

  CVReturn cacheStatus = CVMetalTextureCacheCreate(kCFAllocatorDefault, NULL, device, NULL, &_textureCache);
  if (cacheStatus != kCVReturnSuccess || _textureCache == NULL) {
    VCRFail(errorMessage, [NSString stringWithFormat:@"Failed to create CVMetalTextureCache (status %d).", cacheStatus]);
    return nil;
  }

  _outputBuffer = [device newBufferWithLength:outputByteCount options:MTLResourceStorageModeShared];
  if (_outputBuffer == nil) {
    VCRFail(errorMessage, @"Failed to allocate Metal output buffer.");
    return nil;
  }
  _outputBuffer.label = @"VisionCameraResizer.Output";
  return self;
}

- (void)dealloc {
  [self invalidate];
}

- (void)invalidate {
  if (_textureCache != NULL) {
    CVMetalTextureCacheFlush(_textureCache, 0);
    CFRelease(_textureCache);
    _textureCache = NULL;
  }
  _outputBuffer = nil;
  _pipelineState = nil;
  _commandQueue = nil;
}

- (void *)outputContents {
  return _outputBuffer.contents;
}

- (BOOL)runWithPixelBuffer:(CVPixelBufferRef)pixelBuffer
           rotationDegrees:(int32_t)rotationDegrees
                isMirrored:(BOOL)isMirrored
              errorMessage:(NSString **)errorMessage {
  if (_outputBuffer == nil) {
    return VCRFail(errorMessage, @"This Resizer has already been disposed!");
  }
  OSType pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer);
  if (pixelFormat != kCVPixelFormatType_420YpCbCr8BiPlanarFullRange) {
    return VCRFail(errorMessage, [NSString stringWithFormat:@"Unsupported input pixel format %u. Expected "
                                                            @"kCVPixelFormatType_420YpCbCr8BiPlanarFullRange "
                                                            @"(yuv-420-8-bit-full).",
                                                            (unsigned int)pixelFormat]);
  }
  // Release the autoreleased command buffer and encoder (which reference the camera's textures) right away.
  @autoreleasepool {
    CVMetalTextureRef yPlane =
        VCRCreatePlaneTexture(_textureCache, pixelBuffer, MTLPixelFormatR8Unorm, 0, errorMessage);
    if (yPlane == NULL) {
      return NO;
    }
    CVMetalTextureRef uvPlane =
        VCRCreatePlaneTexture(_textureCache, pixelBuffer, MTLPixelFormatRG8Unorm, 1, errorMessage);
    if (uvPlane == NULL) {
      CFRelease(yPlane);
      return NO;
    }
    BOOL success = [self encodeAndRunWithYTexture:CVMetalTextureGetTexture(yPlane)
                                        uvTexture:CVMetalTextureGetTexture(uvPlane)
                                  rotationDegrees:rotationDegrees
                                       isMirrored:isMirrored
                                     errorMessage:errorMessage];
    // The CoreVideo-backed textures were kept alive until GPU execution finished.
    CFRelease(uvPlane);
    CFRelease(yPlane);
    return success;
  }
}

- (BOOL)encodeAndRunWithYTexture:(id<MTLTexture>)yTexture
                       uvTexture:(id<MTLTexture>)uvTexture
                 rotationDegrees:(int32_t)rotationDegrees
                      isMirrored:(BOOL)isMirrored
                    errorMessage:(NSString **)errorMessage {
  id<MTLCommandBuffer> commandBuffer = [_commandQueue commandBuffer];
  if (commandBuffer == nil) {
    return VCRFail(errorMessage, @"Failed to create Metal command buffer.");
  }
  id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
  if (encoder == nil) {
    return VCRFail(errorMessage, @"Failed to create Metal compute command encoder.");
  }
  VCRResizeUniforms uniforms = {
      .outputWidth = _outputWidth,
      .outputHeight = _outputHeight,
      .rotationDegrees = rotationDegrees,
      .isMirrored = isMirrored ? 1 : 0,
  };
  [encoder setComputePipelineState:_pipelineState];
  [encoder setTexture:yTexture atIndex:0];
  [encoder setTexture:uvTexture atIndex:1];
  [encoder setBuffer:_outputBuffer offset:0 atIndex:0];
  [encoder setBytes:&uniforms length:sizeof(uniforms) atIndex:1];
  // The threadgroup grid covers the full output image, rounded up. The kernels bounds-check `gid`, so this also works
  // on GPUs that do not support non-uniform threadgroups.
  MTLSize threadgroups =
      MTLSizeMake((_outputWidth + _threadsPerThreadgroup.width - 1) / _threadsPerThreadgroup.width,
                  (_outputHeight + _threadsPerThreadgroup.height - 1) / _threadsPerThreadgroup.height, 1);
  [encoder dispatchThreadgroups:threadgroups threadsPerThreadgroup:_threadsPerThreadgroup];
  [encoder endEncoding];

  [commandBuffer commit];
  [commandBuffer waitUntilCompleted];

  if (commandBuffer.status != MTLCommandBufferStatusCompleted) {
    NSString *message = commandBuffer.error.localizedDescription ?: @"Unknown Metal command buffer error.";
    return VCRFail(errorMessage, [NSString stringWithFormat:@"Metal resize pipeline failed (status %ld): %@",
                                                            (long)commandBuffer.status, message]);
  }
  return YES;
}

@end

@implementation VCRMetalResizerPipelineFactory

+ (nullable VCRMetalResizerPipeline *)createPipelineWithShaderSource:(NSString *)shaderSource
                                                        functionName:(NSString *)functionName
                                                         outputWidth:(uint32_t)outputWidth
                                                        outputHeight:(uint32_t)outputHeight
                                                        channelOrder:(uint32_t)channelOrder
                                                         pixelLayout:(uint32_t)pixelLayout
                                                        channelCount:(uint32_t)channelCount
                                                           scaleMode:(uint32_t)scaleMode
                                                     outputByteCount:(NSUInteger)outputByteCount
                                                        errorMessage:(NSString **)errorMessage {
  return [[VCRMetalResizerPipeline alloc] initWithShaderSource:shaderSource
                                                  functionName:functionName
                                                   outputWidth:outputWidth
                                                  outputHeight:outputHeight
                                                  channelOrder:channelOrder
                                                   pixelLayout:pixelLayout
                                                  channelCount:channelCount
                                                     scaleMode:scaleMode
                                               outputByteCount:outputByteCount
                                                  errorMessage:errorMessage];
}

@end
