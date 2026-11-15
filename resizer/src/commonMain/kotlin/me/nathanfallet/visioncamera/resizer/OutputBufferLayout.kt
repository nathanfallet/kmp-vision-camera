package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.CameraOrientation

// Port of V5's OutputBufferLayout.cpp (Android), OutputFormat+Resizer.swift and
// CameraOrientation+shaderRotationDegrees.swift (iOS): the output layout math and the values the GPU shaders expect.

/** The number of channels of an RGB or BGR pixel. */
private const val RGB_CHANNEL_COUNT = 3

/** The number of bytes of a 32-bit float. */
private const val FLOAT32_BYTE_COUNT = 4

/** The shader value of [DataType.FLOAT32]. */
private const val DATA_TYPE_FLOAT32_ORDINAL = 3

/** The rotations of a Frame, in degrees. */
private const val DEGREES_90 = 90
private const val DEGREES_180 = 180
private const val DEGREES_270 = 270

/** The number of packed output channels written for each pixel in this output layout. */
internal val ChannelOrder.channelsPerPixel: Int
    get() = when (this) {
        ChannelOrder.RGB, ChannelOrder.BGR -> RGB_CHANNEL_COUNT
    }

/** The number of bytes used by one output channel for this data type. */
internal val DataType.bytesPerChannel: Int
    get() = when (this) {
        DataType.INT8, DataType.UINT8 -> 1
        DataType.FLOAT16 -> 2
        DataType.FLOAT32 -> FLOAT32_BYTE_COUNT
    }

/**
 * The exact byte count for one tightly packed output image. The [PixelLayout] only changes how channel values are
 * arranged in memory, not how many are stored.
 */
internal fun outputByteCountOf(channelOrder: ChannelOrder, dataType: DataType, width: Int, height: Int): Long =
    width.toLong() * height.toLong() * channelOrder.channelsPerPixel * dataType.bytesPerChannel

/** The exact byte count of one output image of these options. */
internal val ResizerOptions.outputByteCount: Int
    get() = outputByteCountOf(channelOrder, dataType, width, height).toInt()

/** The integer value the GPU shaders expect for this channel order (`0` = RGB, `1` = BGR). */
internal val ChannelOrder.shaderOrdinal: Int
    get() = when (this) {
        ChannelOrder.RGB -> 0
        ChannelOrder.BGR -> 1
    }

/** The integer value the GPU shaders expect for this pixel layout (`0` = interleaved, `1` = planar). */
internal val PixelLayout.shaderOrdinal: Int
    get() = when (this) {
        PixelLayout.INTERLEAVED -> 0
        PixelLayout.PLANAR -> 1
    }

/** The integer value the GPU shaders expect for this scale mode (`0` = cover, `1` = contain, `2` = stretch). */
internal val ScaleMode.shaderOrdinal: Int
    get() = when (this) {
        ScaleMode.COVER -> 0
        ScaleMode.CONTAIN -> 1
        ScaleMode.STRETCH -> 2
    }

/**
 * The integer value the Vulkan shader expects for this data type (`0` = int8, `1` = uint8, `2` = float16,
 * `3` = float32).
 */
internal val DataType.shaderOrdinal: Int
    get() = when (this) {
        DataType.INT8 -> 0
        DataType.UINT8 -> 1
        DataType.FLOAT16 -> 2
        DataType.FLOAT32 -> DATA_TYPE_FLOAT32_ORDINAL
    }

/** The rotation of a Frame with this orientation, in degrees, as the GPU shaders expect it. */
internal val CameraOrientation.shaderRotationDegrees: Int
    get() = when (this) {
        CameraOrientation.UP -> 0
        CameraOrientation.RIGHT -> DEGREES_90
        CameraOrientation.DOWN -> DEGREES_180
        CameraOrientation.LEFT -> DEGREES_270
    }
