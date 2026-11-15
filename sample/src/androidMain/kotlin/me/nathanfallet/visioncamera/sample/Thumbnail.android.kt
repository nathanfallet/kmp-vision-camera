package me.nathanfallet.visioncamera.sample

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import me.nathanfallet.visioncamera.Photo
import me.nathanfallet.visioncamera.toBitmap

actual fun Photo.toThumbnail(): ImageBitmap = toBitmap().asImageBitmap()
