package me.nathanfallet.visioncamera.barcode.extensions

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.ImageIO.CGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationDownMirrored
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationLeftMirrored
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationRightMirrored
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.ImageIO.kCGImagePropertyOrientationUpMirrored
import platform.UIKit.UIImageOrientation
import platform.posix.memcpy

/**
 * Whether the current iOS version is at least [major].[minor], the equivalent of Swift's `#available(iOS x.y, *)`.
 */
internal fun isIOSAtLeast(major: Long, minor: Long = 0): Boolean {
    val version = cValue<NSOperatingSystemVersion> {
        majorVersion = major
        minorVersion = minor
        patchVersion = 0
    }
    return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(version)
}

/** Copies the bytes of this [NSData] into a [ByteArray]. */
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    }
    return bytes
}

/** Converts this [UIImageOrientation] to its [CGImagePropertyOrientation] (EXIF orientation), as Vision expects. */
internal fun UIImageOrientation.toCGImagePropertyOrientation(): CGImagePropertyOrientation = when (this) {
    UIImageOrientation.UIImageOrientationUp -> kCGImagePropertyOrientationUp
    UIImageOrientation.UIImageOrientationUpMirrored -> kCGImagePropertyOrientationUpMirrored
    UIImageOrientation.UIImageOrientationDown -> kCGImagePropertyOrientationDown
    UIImageOrientation.UIImageOrientationDownMirrored -> kCGImagePropertyOrientationDownMirrored
    UIImageOrientation.UIImageOrientationLeft -> kCGImagePropertyOrientationLeft
    UIImageOrientation.UIImageOrientationLeftMirrored -> kCGImagePropertyOrientationLeftMirrored
    UIImageOrientation.UIImageOrientationRight -> kCGImagePropertyOrientationRight
    UIImageOrientation.UIImageOrientationRightMirrored -> kCGImagePropertyOrientationRightMirrored
    // The shared iOS metadata compilation sees UIImageOrientation as an expect enum
    else -> kCGImagePropertyOrientationUp
}
