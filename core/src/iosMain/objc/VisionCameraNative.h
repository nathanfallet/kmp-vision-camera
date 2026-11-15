#pragma once

// Native helpers of VisionCamera's iOS implementation, bound to Kotlin by cinterop (VisionCameraNative.def), and
// compiled into libVisionCameraNative.a (VisionCameraNative.m) by core's buildIosNativeLibrary* tasks.
//
// An Objective-C object that reaches Kotlin/Native stays retained until the Kotlin garbage collector collects its
// wrapper, which may take seconds. Objects backed by a small buffer pool of AVFoundation (the AVDepthData of depth
// outputs, the synchronized data of an AVCaptureDataOutputSynchronizer) would then exhaust the pool and stall the
// stream. These delegates receive them in Objective-C instead, and hand Kotlin an opaque +1 reference (or a Core
// Foundation buffer) that Kotlin releases as soon as the Depth or Frame is disposed, like ARC does in V5's Swift.

#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#import <CoreVideo/CoreVideo.h>
#import <ImageIO/ImageIO.h>

/** An opaque +1 reference to an AVDepthData, released with vc_release. */
typedef void *VCDepthDataRef;

/** Called with a +1 reference to a depth output's AVDepthData, its timestamp and its connection. */
typedef void (^VCDepthDataCallback)(VCDepthDataRef _Nonnull depthData, int64_t timestampValue,
                                    int32_t timestampScale, AVCaptureConnection *_Nonnull connection);

/** Called with the reason of a dropped depth frame. */
typedef void (^VCDroppedCallback)(NSInteger reason);

/** The AVCaptureDepthDataOutputDelegate of a depth output. */
@interface VCDepthDataOutputDelegate : NSObject <AVCaptureDepthDataOutputDelegate>
@property(atomic, copy, nullable) VCDepthDataCallback onDepthData;
@property(atomic, copy, nullable) VCDroppedCallback onDepthDataDropped;
@end

/** The media type of a synchronized sample: video. */
static const NSInteger VCSynchronizedMediaTypeVideo = 0;
/** The media type of a synchronized sample: depth. */
static const NSInteger VCSynchronizedMediaTypeDepth = 1;
/** The media type of a synchronized sample: other (a sample that could not be read). */
static const NSInteger VCSynchronizedMediaTypeOther = 2;

/** Called with a synchronized video sample buffer (+0, retain it to keep it), its timestamp and its connection. */
typedef void (^VCSynchronizedVideoCallback)(CMSampleBufferRef _Nonnull sampleBuffer, int64_t timestampValue,
                                            int32_t timestampScale, AVCaptureConnection *_Nonnull connection);

/** Called with a +1 reference to a synchronized AVDepthData, its timestamp and its connection. */
typedef void (^VCSynchronizedDepthCallback)(VCDepthDataRef _Nonnull depthData, int64_t timestampValue,
                                            int32_t timestampScale, AVCaptureConnection *_Nonnull connection);

/** Called with the media type (VCSynchronizedMediaType...) and reason of a dropped synchronized sample. */
typedef void (^VCSynchronizedDroppedCallback)(NSInteger mediaType, NSInteger reason);

/**
 * The AVCaptureDataOutputSynchronizerDelegate of an output synchronizer. For each synchronized collection, it calls
 * onBegin, then onVideo, onDepth or onDropped for each of the synchronizer's outputs, then onEnd.
 */
@interface VCSynchronizedOutputDelegate : NSObject <AVCaptureDataOutputSynchronizerDelegate>
@property(atomic, copy, nullable) void (^onBegin)(void);
@property(atomic, copy, nullable) VCSynchronizedVideoCallback onVideo;
@property(atomic, copy, nullable) VCSynchronizedDepthCallback onDepth;
@property(atomic, copy, nullable) VCSynchronizedDroppedCallback onDropped;
@property(atomic, copy, nullable) void (^onEnd)(void);
@end

/** Retains the given AVDepthData, and returns a +1 reference to it. */
static inline VCDepthDataRef _Nonnull vc_depth_retain(AVDepthData *_Nonnull depthData) {
  return (VCDepthDataRef)CFRetain((__bridge CFTypeRef)depthData);
}

/** Releases a +1 reference returned by these helpers. */
static inline void vc_release(void *_Nonnull ref) { CFRelease((CFTypeRef)ref); }

/** The AVDepthData of a reference, which Kotlin/Native retains until its wrapper is garbage collected. */
static inline AVDepthData *_Nonnull vc_depth_object(VCDepthDataRef _Nonnull depthData) {
  return (__bridge AVDepthData *)depthData;
}

static inline CVPixelBufferRef _Nonnull vc_depth_map(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).depthDataMap;
}

static inline OSType vc_depth_type(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).depthDataType;
}

static inline BOOL vc_depth_is_filtered(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).depthDataFiltered;
}

static inline NSInteger vc_depth_accuracy(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).depthDataAccuracy;
}

static inline NSInteger vc_depth_quality(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).depthDataQuality;
}

static inline NSArray<NSNumber *> *_Nonnull vc_depth_available_types(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).availableDepthDataTypes;
}

static inline AVCameraCalibrationData *_Nullable vc_depth_calibration(VCDepthDataRef _Nonnull depthData) {
  return ((__bridge AVDepthData *)depthData).cameraCalibrationData;
}

/** A +1 reference to the given depth data, rotated by the given EXIF orientation. */
static inline VCDepthDataRef _Nonnull vc_depth_retain_applying_exif(VCDepthDataRef _Nonnull depthData,
                                                                    CGImagePropertyOrientation orientation) {
  AVDepthData *rotated = [(__bridge AVDepthData *)depthData depthDataByApplyingExifOrientation:orientation];
  return (VCDepthDataRef)CFRetain((__bridge CFTypeRef)rotated);
}

/** A +1 reference to the given depth data, converted to the given pixel format. */
static inline VCDepthDataRef _Nonnull vc_depth_retain_converting(VCDepthDataRef _Nonnull depthData, OSType type) {
  AVDepthData *converted = [(__bridge AVDepthData *)depthData depthDataByConvertingToDepthDataType:type];
  return (VCDepthDataRef)CFRetain((__bridge CFTypeRef)converted);
}
