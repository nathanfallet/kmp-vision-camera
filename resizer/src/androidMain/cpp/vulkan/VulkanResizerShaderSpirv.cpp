///
/// VulkanResizerShaderSpirv.cpp
/// VisionCamera
/// Copyright © 2026 Marc Rousavy @ Margelo
/// Ported from React Native VisionCamera's resizer package (MIT, see NOTICE).
///

#include "vulkan/VulkanResizerShaderSpirv.hpp"

#include <iterator>

namespace visioncamera::resizer::vulkan {

namespace {

  // Resizer.comp, compiled to SPIR-V by the NDK's glslc in the compileResizerShader Gradle task. Its -mfmt=num output
  // is a list of comma-separated 32-bit words.
  constexpr uint32_t kResizerComputeShaderWords[] = {
#include "Resizer.comp.spv.inc"
  };

} // namespace

const std::vector<uint32_t>& getResizerComputeShaderSpirv() {
  static const std::vector<uint32_t> shaderWords(std::begin(kResizerComputeShaderWords), std::end(kResizerComputeShaderWords));
  return shaderWords;
}

} // namespace visioncamera::resizer::vulkan
