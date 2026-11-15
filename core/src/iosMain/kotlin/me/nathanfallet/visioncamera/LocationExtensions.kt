package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import platform.AVFoundation.AVMetadataKeySpaceQuickTimeMetadata
import platform.AVFoundation.AVMetadataQuickTimeMetadataKeyLocationISO6709
import platform.AVFoundation.AVMetadataQuickTimeMetadataKeySoftware
import platform.AVFoundation.AVMutableMetadataItem
import platform.AVFoundation.key
import platform.AVFoundation.keySpace
import platform.CoreMedia.kCMMetadataBaseDataType_UTF8
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithFormat
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.ImageIO.kCGImagePropertyGPSAltitude
import platform.ImageIO.kCGImagePropertyGPSAltitudeRef
import platform.ImageIO.kCGImagePropertyGPSDateStamp
import platform.ImageIO.kCGImagePropertyGPSLatitude
import platform.ImageIO.kCGImagePropertyGPSLatitudeRef
import platform.ImageIO.kCGImagePropertyGPSLongitude
import platform.ImageIO.kCGImagePropertyGPSLongitudeRef
import platform.ImageIO.kCGImagePropertyGPSTimeStamp

// Port of V5's CLLocation+toEXIF.swift, CLLocation+toISO6709String.swift, CLLocation+toAVMutableMetadataItem.swift
// and AVMetadataItem+libraryTag.swift. V5 wraps a `CLLocation`, we read the common [Location] directly.

private const val MAX_LATITUDE = 90.0
private const val MILLISECONDS_PER_SECOND = 1_000.0
private const val MAX_LONGITUDE = 180.0

/** Converts this [Location] to an EXIF GPS dictionary (`kCGImagePropertyGPSDictionary`). */
internal fun Location.toEXIF(): Map<Any?, Any?> {
    val gps = mutableMapOf<Any?, Any?>()

    // GPS timestamps should be UTC
    val date = NSDate.dateWithTimeIntervalSince1970(timestamp / MILLISECONDS_PER_SECOND)
    val dateStamp = gpsDateFormatter("yyyy:MM:dd").stringFromDate(date)
    val timeStamp = gpsDateFormatter("HH:mm:ss.SSS").stringFromDate(date)

    var lat = latitude
    var lon = longitude
    var latRef = "N"
    var lonRef = "E"
    if (lat < 0) {
        lat = -lat
        latRef = "S"
    }
    if (lon < 0) {
        lon = -lon
        lonRef = "W"
    }

    gps[kCGImagePropertyGPSDateStamp.toKotlinString()] = dateStamp
    gps[kCGImagePropertyGPSTimeStamp.toKotlinString()] = timeStamp
    gps[kCGImagePropertyGPSLatitudeRef.toKotlinString()] = latRef
    gps[kCGImagePropertyGPSLatitude.toKotlinString()] = lat
    gps[kCGImagePropertyGPSLongitudeRef.toKotlinString()] = lonRef
    gps[kCGImagePropertyGPSLongitude.toKotlinString()] = lon

    // Altitude only if we have a valid vertical accuracy
    if (verticalAccuracy >= 0) {
        var alt = altitude
        val altRef = if (alt < 0) 1 else 0
        if (alt < 0) alt = -alt
        gps[kCGImagePropertyGPSAltitudeRef.toKotlinString()] = altRef
        gps[kCGImagePropertyGPSAltitude.toKotlinString()] = alt
    }
    return gps
}

private fun gpsDateFormatter(format: String): NSDateFormatter = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    dateFormat = format
}

/**
 * Converts this [Location] to an ISO-6709 string (e.g. `+48.856600+002.352200+35.0/`).
 *
 * @throws VisionCameraException If the coordinates are invalid.
 */
internal fun Location.toISO6709String(): String {
    val lat = latitude
    val lon = longitude
    val isFinite = lat.isFinite() && lon.isFinite()
    val isInRange = lat in -MAX_LATITUDE..MAX_LATITUDE && lon in -MAX_LONGITUDE..MAX_LONGITUDE
    if (!isFinite || !isInRange) {
        throw VisionCameraException("Location ($lat, $lon) is not a valid coordinate!")
    }

    // No field widths needed; the important part is forced sign and fixed fraction digits (~0.11 m at 6 digits).
    var result = NSString.stringWithFormat("%+.6f", normalizeNegativeZero(lat)) +
        NSString.stringWithFormat("%+.6f", normalizeNegativeZero(lon))
    if (verticalAccuracy >= 0 && altitude.isFinite()) {
        // Decimeters by default
        result += NSString.stringWithFormat("%+.1f", normalizeNegativeZero(altitude))
    }
    return "$result/"
}

private fun normalizeNegativeZero(value: Double): Double = if (value == 0.0) 0.0 else value

/** Converts this [Location] to a QuickTime ISO-6709 location metadata item, for video files. */
@OptIn(BetaInteropApi::class)
internal fun Location.toAVMutableMetadataItem(): AVMutableMetadataItem = AVMutableMetadataItem().apply {
    keySpace = AVMetadataKeySpaceQuickTimeMetadata
    key = NSString.create(string = requireNotNull(AVMetadataQuickTimeMetadataKeyLocationISO6709))
    setValue(NSString.create(string = toISO6709String()))
    setDataType(kCMMetadataBaseDataType_UTF8.toKotlinString())
}

/** Creates the QuickTime metadata item tagging video files as recorded by VisionCamera. */
@OptIn(BetaInteropApi::class)
internal fun libraryTagMetadataItem(): AVMutableMetadataItem = AVMutableMetadataItem().apply {
    keySpace = AVMetadataKeySpaceQuickTimeMetadata
    key = NSString.create(string = requireNotNull(AVMetadataQuickTimeMetadataKeySoftware))
    setValue(NSString.create(string = "VisionCamera"))
}
