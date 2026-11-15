#pragma once

// The Metal pipeline of the resizer's iOS implementation (port of V5's ios/Metal/*.swift), bound to Kotlin by cinterop
// (VisionCameraResizerNative.def), and compiled into libVisionCameraResizerNative.a by the resizer's
// buildIosNativeLibrary* tasks.
//
// It is written in Objective-C rather than Kotlin because an Objective-C object that reaches Kotlin/Native stays
// retained until the Kotlin garbage collector collects its wrapper, which may take seconds. The per-frame objects of
// this pipeline (the CVMetalTexture and MTLTexture wrapping the camera's CVPixelBuffer, the command buffer that
// references them) would then keep the camera's buffers out of its small pool and stall the stream. Here, ARC releases
// them as soon as each frame is resized, like V5's Swift.

#import <CoreVideo/CoreVideo.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Owns the Metal runtime objects needed to run the resize kernel for one fixed output layout (V5's
 * MetalResizerPipeline), and its reusable output buffer (V5's MetalReusableBuffer). Its caller (Kotlin's
 * MetalResizerPipeline) ensures only one frame uses the output buffer at a time. Create it with
 * VCRMetalResizerPipelineFactory.
 */
@interface VCRMetalResizerPipeline : NSObject

- (instancetype)init NS_UNAVAILABLE;

/**
 * Runs the resize kernel for one camera frame (a `kCVPixelFormatType_420YpCbCr8BiPlanarFullRange` pixel buffer), and
 * waits until the output buffer is written. On failure, returns NO and sets `errorMessage`.
 */
- (BOOL)runWithPixelBuffer:(CVPixelBufferRef)pixelBuffer
           rotationDegrees:(int32_t)rotationDegrees
                isMirrored:(BOOL)isMirrored
              errorMessage:(NSString *_Nullable *_Nullable)errorMessage;

/**
 * Releases the Metal objects and the output buffer right away, instead of when Kotlin's garbage collector collects
 * this pipeline. It must not be used afterwards.
 */
- (void)invalidate;

/** The CPU address of the shared output buffer, or NULL once invalidated. */
@property(nonatomic, readonly, nullable) void *outputContents;

/** The exact byte count of one output image. */
@property(nonatomic, readonly) NSUInteger outputByteCount;

@end

/** Creates VCRMetalResizerPipelines (a class method of another class, so that Kotlin can get nil and an error). */
@interface VCRMetalResizerPipelineFactory : NSObject

/**
 * Builds the Metal pipeline for one fixed output size and output layout. On failure, returns nil and sets
 * `errorMessage`.
 *
 * @param shaderSource The source of ResizerKernels.metal, compiled once per process.
 * @param functionName The kernel to run, e.g. `resize_float32`.
 * @param channelOrder The `kChannelOrder` function constant (0 = RGB, 1 = BGR).
 * @param pixelLayout The `kPixelLayout` function constant (0 = interleaved, 1 = planar).
 * @param channelCount The `kChannelCount` function constant (3 for RGB/BGR).
 * @param scaleMode The `kScaleMode` function constant (0 = cover, 1 = contain, 2 = stretch).
 * @param outputByteCount The exact byte count of one output image.
 */
+ (nullable VCRMetalResizerPipeline *)createPipelineWithShaderSource:(NSString *)shaderSource
                                                        functionName:(NSString *)functionName
                                                         outputWidth:(uint32_t)outputWidth
                                                        outputHeight:(uint32_t)outputHeight
                                                        channelOrder:(uint32_t)channelOrder
                                                         pixelLayout:(uint32_t)pixelLayout
                                                        channelCount:(uint32_t)channelCount
                                                           scaleMode:(uint32_t)scaleMode
                                                     outputByteCount:(NSUInteger)outputByteCount
                                                        errorMessage:(NSString *_Nullable *_Nullable)errorMessage;

@end

/** Whether Metal is available on this device (V5's HybridResizerFactory.isAvailable). */
BOOL vcr_isMetalAvailable(void);

NS_ASSUME_NONNULL_END
