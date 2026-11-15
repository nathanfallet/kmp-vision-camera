// The implementations of VisionCameraNative.h.

#import "VisionCameraNative.h"

@implementation VCDepthDataOutputDelegate
- (void)depthDataOutput:(AVCaptureDepthDataOutput *)output
     didOutputDepthData:(AVDepthData *)depthData
              timestamp:(CMTime)timestamp
             connection:(AVCaptureConnection *)connection {
  VCDepthDataCallback callback = self.onDepthData;
  if (callback == nil) return;
  callback((VCDepthDataRef)CFRetain((__bridge CFTypeRef)depthData), timestamp.value, timestamp.timescale,
           connection);
}

- (void)depthDataOutput:(AVCaptureDepthDataOutput *)output
       didDropDepthData:(AVDepthData *)depthData
              timestamp:(CMTime)timestamp
             connection:(AVCaptureConnection *)connection
                 reason:(AVCaptureOutputDataDroppedReason)reason {
  VCDroppedCallback callback = self.onDepthDataDropped;
  if (callback != nil) callback(reason);
}
@end

@implementation VCSynchronizedOutputDelegate
- (void)dataOutputSynchronizer:(AVCaptureDataOutputSynchronizer *)synchronizer
    didOutputSynchronizedDataCollection:(AVCaptureSynchronizedDataCollection *)collection {
  void (^onBegin)(void) = self.onBegin;
  void (^onEnd)(void) = self.onEnd;
  VCSynchronizedVideoCallback onVideo = self.onVideo;
  VCSynchronizedDepthCallback onDepth = self.onDepth;
  VCSynchronizedDroppedCallback onDropped = self.onDropped;
  if (onBegin == nil || onEnd == nil || onVideo == nil || onDepth == nil || onDropped == nil) return;

  onBegin();
  for (AVCaptureOutput *output in synchronizer.dataOutputs) {
    AVCaptureSynchronizedData *data = [collection synchronizedDataForCaptureOutput:output];
    if (data == nil) {
      NSLog(@"VisionCamera: AVCaptureSynchronizedDataCollection does not contain data for %@!", output);
      continue;
    }
    if ([data isKindOfClass:[AVCaptureSynchronizedSampleBufferData class]]) {
      // Video Frame
      AVCaptureSynchronizedSampleBufferData *video = (AVCaptureSynchronizedSampleBufferData *)data;
      if (video.sampleBufferWasDropped) {
        onDropped(VCSynchronizedMediaTypeVideo, video.droppedReason);
        continue;
      }
      AVCaptureConnection *connection = [output connectionWithMediaType:AVMediaTypeVideo];
      CMSampleBufferRef sampleBuffer = video.sampleBuffer;
      if (connection == nil || sampleBuffer == NULL) {
        NSLog(@"VisionCamera [ERROR]: Failed to capture AVCaptureSynchronizedData %@!", data);
        onDropped(VCSynchronizedMediaTypeOther, AVCaptureOutputDataDroppedReasonNone);
        continue;
      }
      onVideo(sampleBuffer, video.timestamp.value, video.timestamp.timescale, connection);
    } else if ([data isKindOfClass:[AVCaptureSynchronizedDepthData class]]) {
      // Depth Frame
      AVCaptureSynchronizedDepthData *depth = (AVCaptureSynchronizedDepthData *)data;
      if (depth.depthDataWasDropped) {
        onDropped(VCSynchronizedMediaTypeDepth, depth.droppedReason);
        continue;
      }
      AVCaptureConnection *connection = [output connectionWithMediaType:AVMediaTypeDepthData];
      if (connection == nil) {
        NSLog(@"VisionCamera [ERROR]: Failed to capture AVCaptureSynchronizedData %@!", data);
        onDropped(VCSynchronizedMediaTypeOther, AVCaptureOutputDataDroppedReasonNone);
        continue;
      }
      onDepth((VCDepthDataRef)CFRetain((__bridge CFTypeRef)depth.depthData), depth.timestamp.value,
              depth.timestamp.timescale, connection);
    } else {
      // ????? Frame
      NSLog(@"VisionCamera [ERROR]: SynchronizedOutputDelegate received an unknown data type! %@", data);
    }
  }
  onEnd();
}
@end
