///
/// VulkanDynamicDispatch.hpp
/// VisionCamera
/// Copyright © 2026 Marc Rousavy @ Margelo
/// Ported from React Native VisionCamera's resizer package (MIT, see NOTICE).
///

#pragma once

#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

namespace visioncamera::resizer::vulkan {

/**
 * Holds Vulkan instance functions that must be resolved from the runtime loader.
 */
struct VulkanInstanceDispatch final {
  PFN_vkGetPhysicalDeviceFeatures2 getPhysicalDeviceFeatures2{nullptr};

  /**
   * Loads the required instance-level Vulkan functions for this pipeline.
   *
   * @throws If any of the functions in `VulkanInstanceDispatch` could not be found on the system.
   */
  [[nodiscard]] static VulkanInstanceDispatch load(VkInstance instance);
};

/**
 * Holds Vulkan device functions that must be resolved from the runtime loader.
 */
struct VulkanDeviceDispatch final {
  PFN_vkGetAndroidHardwareBufferPropertiesANDROID getAndroidHardwareBufferPropertiesANDROID{nullptr};
  PFN_vkCreateSamplerYcbcrConversion createSamplerYcbcrConversion{nullptr};
  PFN_vkDestroySamplerYcbcrConversion destroySamplerYcbcrConversion{nullptr};

  /**
   * Loads the required device-level Vulkan functions for this pipeline.
   *
   * @throws If any of the functions in `VulkanDeviceDispatch` could not be found on the system.
   */
  [[nodiscard]] static VulkanDeviceDispatch load(VkDevice device);
};

} // namespace visioncamera::resizer::vulkan
