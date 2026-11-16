package me.nathanfallet.visioncamera.sample

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
actual fun fileSize(path: String): Long? =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue
