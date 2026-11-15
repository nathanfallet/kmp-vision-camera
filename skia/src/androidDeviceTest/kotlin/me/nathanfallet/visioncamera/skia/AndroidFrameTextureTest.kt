package me.nathanfallet.visioncamera.skia

import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.filters.SdkSuppress
import me.nathanfallet.visioncamera.NativeBuffer
import org.jetbrains.skia.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wraps a real HardwareBuffer as a Skia GPU image (the zero-copy path of [renderToTexture]: EGLImage + external
 * texture), draws it and reads it back. Run it with `./gradlew :skia:connectedAndroidDeviceTest` (API 31+).
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
@RequiresApi(Build.VERSION_CODES.S)
class AndroidFrameTextureTest {
    private val width = 64
    private val height = 32

    @Test
    fun hardwareBuffersAreDrawable() {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val hardwareBuffer = bitmap.copy(Bitmap.Config.HARDWARE, false).hardwareBuffer
        val nativeBuffer = HardwareBufferNativeBuffer(hardwareBuffer)
        val image = surfacesCache.withSurface(width, height) { context, surface ->
            val frameTexture = context.makeFrameTexture(nativeBuffer)
            assertEquals(width, frameTexture.image.width)
            assertEquals(height, frameTexture.image.height)
            surface.canvas.drawImage(frameTexture.image, 0f, 0f)
            val snapshot = surface.makeImageSnapshot()
            frameTexture.release()
            nativeBuffer.release()
            try {
                snapshot.makeNonTextureImage()
            } finally {
                snapshot.close()
            }
        }
        val color = org.jetbrains.skia.Bitmap.makeFromImage(image).getColor(width / 2, height / 2)
        image.close()
        clearSurfacesCache()
        assertTrue(color.toUInt().toString(16), Color.getR(color) > 230)
        assertTrue(color.toUInt().toString(16), Color.getG(color) < 30)
        assertTrue(color.toUInt().toString(16), Color.getB(color) < 30)
    }

    /** An `AHardwareBuffer*` reference like a Frame's, from core's (internal) JNI helper. */
    private class HardwareBufferNativeBuffer(hardwareBuffer: HardwareBuffer) : NativeBuffer {
        private val helper = Class.forName("me.nathanfallet.visioncamera.NativeBufferHelper")

        override val pointer: Long = helper.getMethod("getHardwareBufferPointer", HardwareBuffer::class.java)
            .invoke(null, hardwareBuffer) as Long

        override fun release() {
            helper.getMethod("releaseHardwareBufferPointer", Long::class.java).invoke(null, pointer)
        }
    }
}
