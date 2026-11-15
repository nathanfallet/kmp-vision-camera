package me.nathanfallet.visioncamera.resizer

import me.nathanfallet.visioncamera.CameraOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OutputBufferLayoutTest {
    private fun options(
        width: Int = 192,
        height: Int = 192,
        channelOrder: ChannelOrder = ChannelOrder.RGB,
        dataType: DataType = DataType.FLOAT32,
    ) = ResizerOptions(width, height, channelOrder, dataType, ScaleMode.COVER, PixelLayout.PLANAR)

    @Test
    fun outputByteCountIsWidthTimesHeightTimesChannelsTimesBytes() {
        assertEquals(192 * 192 * 3 * 4, options(dataType = DataType.FLOAT32).outputByteCount)
        assertEquals(192 * 192 * 3 * 2, options(dataType = DataType.FLOAT16).outputByteCount)
        assertEquals(192 * 192 * 3, options(dataType = DataType.UINT8).outputByteCount)
        assertEquals(320 * 240 * 3, options(width = 320, height = 240, dataType = DataType.INT8).outputByteCount)
        assertEquals(3, options(width = 1, height = 1, channelOrder = ChannelOrder.BGR, dataType = DataType.UINT8)
            .outputByteCount)
    }

    @Test
    fun optionsRejectEmptyOrOversizedOutputs() {
        assertFailsWith<IllegalArgumentException> { options(width = 0) }
        assertFailsWith<IllegalArgumentException> { options(height = -1) }
        // 2^31 bytes do not fit in a ByteArray.
        assertFailsWith<IllegalArgumentException> { options(width = 32768, height = 16384) }
    }

    @Test
    fun shaderOrdinalsMatchTheShaders() {
        // ResizerKernels.metal, Resizer.comp and ResizerOptions.hpp (JNI) rely on these values.
        assertEquals(listOf(0, 1), ChannelOrder.entries.map { it.shaderOrdinal })
        assertEquals(listOf(0, 1), PixelLayout.entries.map { it.shaderOrdinal })
        assertEquals(listOf(0, 1, 2), ScaleMode.entries.map { it.shaderOrdinal })
        assertEquals(listOf(0, 1, 2, 3), DataType.entries.map { it.shaderOrdinal })
        // ResizerJni.cpp casts the Kotlin values to its enums.
        ChannelOrder.entries.forEach { assertEquals(it.ordinal, it.shaderOrdinal) }
        PixelLayout.entries.forEach { assertEquals(it.ordinal, it.shaderOrdinal) }
        ScaleMode.entries.forEach { assertEquals(it.ordinal, it.shaderOrdinal) }
        DataType.entries.forEach { assertEquals(it.ordinal, it.shaderOrdinal) }
    }

    @Test
    fun rotationDegreesFollowTheFrameOrientation() {
        assertEquals(0, CameraOrientation.UP.shaderRotationDegrees)
        assertEquals(90, CameraOrientation.RIGHT.shaderRotationDegrees)
        assertEquals(180, CameraOrientation.DOWN.shaderRotationDegrees)
        assertEquals(270, CameraOrientation.LEFT.shaderRotationDegrees)
    }
}
