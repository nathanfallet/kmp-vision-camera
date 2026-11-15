package me.nathanfallet.visioncamera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.ImageReaderProxy
import java.util.concurrent.Executor

/**
 * An implementation of [ImageReaderProxy] that streams [android.media.Image]s in [ImageFormat.DEPTH16].
 */
@SuppressLint("RestrictedApi")
internal class DepthImageReaderProxy(width: Int, height: Int, maxImages: Int, usage: Long) : ImageReaderProxy {
    private val imageReaderThread = HandlerThread("DepthImageReaderProxy").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private val imageReader: ImageReader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ImageReader.newInstance(width, height, ImageFormat.DEPTH16, maxImages, usage)
        } else {
            ImageReader.newInstance(width, height, ImageFormat.DEPTH16, maxImages)
        }

    override fun acquireLatestImage(): ImageProxy? =
        imageReader.acquireLatestImage()?.let { DepthImageProxy(it) }

    override fun acquireNextImage(): ImageProxy? =
        imageReader.acquireNextImage()?.let { DepthImageProxy(it) }

    override fun close() {
        imageReader.close()
        imageReaderThread.quitSafely()
    }

    override fun getHeight(): Int = imageReader.height

    override fun getWidth(): Int = imageReader.width

    override fun getImageFormat(): Int = imageReader.imageFormat

    override fun getMaxImages(): Int = imageReader.maxImages

    override fun getSurface(): Surface? = imageReader.surface

    override fun setOnImageAvailableListener(listener: ImageReaderProxy.OnImageAvailableListener, executor: Executor) {
        imageReader.setOnImageAvailableListener(
            { executor.execute { listener.onImageAvailable(this) } },
            imageReaderHandler,
        )
    }

    override fun clearOnImageAvailableListener() {
        imageReader.setOnImageAvailableListener(null, null)
    }
}
