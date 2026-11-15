package me.nathanfallet.visioncamera.barcode.parser

import me.nathanfallet.visioncamera.barcode.BarcodeValueType

// The parser of the AAMVA driver's licenses and ID cards of BarcodeValueParser, matching ML Kit on Android (measured
// on a Pixel 7a): the header must be valid, and the display value is the document type, last name and first name.

/** The markers of an AAMVA header: `ANSI ` (since 2000), or `AAMVA` (earlier). */
private val aamvaMarkers = listOf("ANSI ", "AAMVA")

/** The subfile types of a driver's license (`DL`) and of an ID card (`ID`). */
private val aamvaDocumentTypes = listOf("DL", "ID")

/** The characters before the AAMVA marker: `@`, then the data element separator, record separator and terminator. */
private const val AAMVA_MARKER_INDEX = 4

/** The length of the issuer identification number, AAMVA version and jurisdiction version after the marker. */
private const val AAMVA_VERSIONS_LENGTH = 10

/** The length of the number of entries, of a subfile type, and of a subfile's offset and length. */
private const val AAMVA_ENTRIES_LENGTH = 2
private const val AAMVA_TYPE_LENGTH = 2
private const val AAMVA_NUMBER_LENGTH = 4

/** The length of a subfile designator: its type, offset and length. */
private const val AAMVA_DESIGNATOR_LENGTH = AAMVA_TYPE_LENGTH + 2 * AAMVA_NUMBER_LENGTH

/** The length of a data element identifier (e.g. `DCS`). */
private const val AAMVA_ELEMENT_ID_LENGTH = 3

/** A subfile designator of an AAMVA header. */
private data class AamvaSubfile(val type: String, val offset: Int, val length: Int)

/**
 * `@\n\u001e\rANSI 636014080102DL00410288ZC03290024DLDAQ...`: an AAMVA driver's license or ID card, displayed as
 * `DL LAST FIRST` (or `ID LAST FIRST`) like ML Kit. Returns `null` (plain text) if the [rawValue] has no valid AAMVA
 * header, or no driver's license or ID card subfile that fits in the [rawValue].
 */
internal fun parseDriverLicense(rawValue: String): ParsedBarcodeValue? {
    val subfile = documentSubfile(rawValue) ?: return null
    val elements = rawValue.substring(subfile.offset, subfile.offset + subfile.length)
        .removePrefix(subfile.type)
        .split('\n')
        .map { it.trim() }
        .filter { it.length >= AAMVA_ELEMENT_ID_LENGTH }
        .associate { it.take(AAMVA_ELEMENT_ID_LENGTH) to it.drop(AAMVA_ELEMENT_ID_LENGTH) }
    val lastName = elements["DCS"].orEmpty()
    val firstName = elements["DAC"] ?: elements["DCT"]?.substringBefore(',').orEmpty()
    return ParsedBarcodeValue(BarcodeValueType.DRIVER_LICENSE, "${subfile.type} $lastName $firstName")
}

/**
 * The first driver's license or ID card subfile of the AAMVA header of [rawValue], or `null` if it has no valid header
 * (`@`, 3 separators, the marker, versions, number of entries and subfile designators), or no such subfile that fits
 * in the [rawValue].
 */
private fun documentSubfile(rawValue: String): AamvaSubfile? {
    val marker = rawValue.drop(AAMVA_MARKER_INDEX).take(aamvaMarkers.first().length)
        .takeIf { rawValue.startsWith("@") && it in aamvaMarkers }
    val entriesIndex = marker?.let { AAMVA_MARKER_INDEX + it.length + AAMVA_VERSIONS_LENGTH }
    val entries = entriesIndex?.let { rawValue.digitsAt(it, AAMVA_ENTRIES_LENGTH) } ?: 0
    val subfiles = (0 until entries).map {
        rawValue.subfileAt(entriesIndex!! + AAMVA_ENTRIES_LENGTH + it * AAMVA_DESIGNATOR_LENGTH)
    }
    return subfiles.takeIf { null !in it }
        ?.firstOrNull { it?.type in aamvaDocumentTypes }
        ?.takeIf { it.offset + it.length <= rawValue.length }
}

/** The number written with [length] digits at [index], or `null` if they are not digits. */
private fun String.digitsAt(index: Int, length: Int): Int? =
    drop(index).take(length).takeIf { it.length == length && it.all(Char::isDigit) }?.toInt()

/** The subfile designator at [index] (a type, then a 4-digit offset and length), or `null` if it is not one. */
private fun String.subfileAt(index: Int): AamvaSubfile? {
    val type = drop(index).take(AAMVA_TYPE_LENGTH)
    val offset = digitsAt(index + AAMVA_TYPE_LENGTH, AAMVA_NUMBER_LENGTH)
    val length = digitsAt(index + AAMVA_TYPE_LENGTH + AAMVA_NUMBER_LENGTH, AAMVA_NUMBER_LENGTH)
    return if (offset != null && length != null) AamvaSubfile(type, offset, length) else null
}
