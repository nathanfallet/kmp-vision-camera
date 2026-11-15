///
/// VulkanResizerShaderSpirv.hpp
/// VisionCamera
/// Copyright © 2026 Marc Rousavy @ Margelo
/// Ported from React Native VisionCamera's resizer package (MIT, see NOTICE): V5's VulkanShaderAssetLoader reads the
/// SPIR-V from the app's assets, here it is embedded in the library at build time, so apps need no extra setup.
///

#pragma once

#include <cstdint>
#include <vector>

namespace visioncamera::resizer::vulkan {

/**
 * Returns the precompiled resizer compute shader (Resizer.comp), embedded in this library as SPIR-V words.
 */
[[nodiscard]] const std::vector<uint32_t>& getResizerComputeShaderSpirv();

} // namespace visioncamera::resizer::vulkan
