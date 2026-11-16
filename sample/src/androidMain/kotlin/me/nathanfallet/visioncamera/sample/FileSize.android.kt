package me.nathanfallet.visioncamera.sample

import java.io.File

actual fun fileSize(path: String): Long? = File(path).takeIf { it.exists() }?.length()
