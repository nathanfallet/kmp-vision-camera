package me.nathanfallet.visioncamera

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.UTType
import platform.posix.memcpy

// Port of V5's URL+createTempURL.swift, plus Foundation bridging helpers.

/** Bridges this `CFStringRef` constant (e.g. an ImageIO or CoreMedia key) to a Kotlin [String]. */
internal fun CFStringRef?.toKotlinString(): String =
    CFBridgingRelease(CFRetain(this)) as? String
        ?: throw VisionCameraException("Failed to bridge a CFString to a Kotlin String!")

/** Copies the bytes of this [NSData] into a new [ByteArray]. */
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
    return bytes
}

/**
 * Creates a temporary file path of the given [fileType] in the temporary directory, using a random `UUID` as its
 * name.
 *
 * @throws VisionCameraException If the [fileType] has no file extension.
 */
internal fun createTempFilePath(fileType: UTType): String {
    val fileExtension = fileType.preferredFilenameExtension
        ?: throw VisionCameraException("File type \"${fileType.identifier}\" has no file extension!")
    val tempDirectory = NSTemporaryDirectory().removeSuffix("/")
    val fileName = NSUUID().UUIDString
    return "$tempDirectory/$fileName.$fileExtension"
}

/**
 * Creates all parent directories of the given file [path], if they don't exist yet.
 *
 * @throws VisionCameraException If the directories could not be created.
 */
internal fun createParentDirectories(path: String) {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isEmpty()) return
    throwingNSError("Failed to create the directory \"$parent\"") { error ->
        NSFileManager.defaultManager.createDirectoryAtPath(
            parent,
            withIntermediateDirectories = true,
            attributes = null,
            error = error,
        )
    }
}
