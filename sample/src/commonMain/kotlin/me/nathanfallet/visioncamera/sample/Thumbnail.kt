package me.nathanfallet.visioncamera.sample

import androidx.compose.ui.graphics.ImageBitmap
import me.nathanfallet.visioncamera.Photo

/** Converts this [Photo] into an upright [ImageBitmap] to display as a thumbnail. */
expect fun Photo.toThumbnail(): ImageBitmap
