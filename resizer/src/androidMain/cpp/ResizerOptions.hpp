///
/// ResizerOptions.hpp
/// VisionCamera
///
/// The resizer's output options and enums, which Nitro generates from V5's TypeScript specs (OutputFormat.ts,
/// ResizerFactory.nitro.ts). The enum values are the ordinals of the Kotlin enums, passed through JNI.
///

#pragma once

#include <cstdint>

namespace visioncamera::resizer {

/**
 * How the three output channels are ordered in memory (Kotlin's `ChannelOrder`).
 */
enum class ChannelOrder : int32_t { RGB = 0, BGR = 1 };

/**
 * The scalar encoding of each output channel (Kotlin's `DataType`).
 */
enum class DataType : int32_t { INT8 = 0, UINT8 = 1, FLOAT16 = 2, FLOAT32 = 3 };

/**
 * How the channels of each pixel are arranged in memory (Kotlin's `PixelLayout`).
 */
enum class PixelLayout : int32_t { INTERLEAVED = 0, PLANAR = 1 };

/**
 * How the input is mapped into the output bounds when the aspect ratios differ (Kotlin's `ScaleMode`).
 */
enum class ScaleMode : int32_t { COVER = 0, CONTAIN = 1, STRETCH = 2 };

/**
 * Configures the output of a resizer pipeline (Kotlin's `ResizerOptions`).
 */
struct ResizerOptions final {
  double width{0};
  double height{0};
  ChannelOrder channelOrder{ChannelOrder::RGB};
  DataType dataType{DataType::UINT8};
  ScaleMode scaleMode{ScaleMode::COVER};
  PixelLayout pixelLayout{PixelLayout::INTERLEAVED};
};

} // namespace visioncamera::resizer
