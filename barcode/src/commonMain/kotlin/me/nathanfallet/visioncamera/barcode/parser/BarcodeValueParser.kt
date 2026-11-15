package me.nathanfallet.visioncamera.barcode.parser

import me.nathanfallet.visioncamera.barcode.Barcode
import me.nathanfallet.visioncamera.barcode.BarcodeFormat
import me.nathanfallet.visioncamera.barcode.BarcodeValueType

/**
 * The semantic type and user-friendly value of a [Barcode]'s raw value.
 */
internal data class ParsedBarcodeValue(
    /** The semantic type of the value. */
    val valueType: BarcodeValueType,
    /** The value in a user-friendly format, or `null` if there is no value. */
    val displayValue: String?,
)

/**
 * Classifies a barcode's raw value into a [BarcodeValueType] and computes its display value, reproducing ML Kit's
 * `Barcode.valueType` and `Barcode.displayValue` for scanners that do not parse values themselves (Apple Vision on
 * iOS).
 *
 * The rules follow ML Kit's value types: well-known prefixes (`http://`, `MEBKM:`, `mailto:`, `MATMSG:`, `tel:`,
 * `SMSTO:`, `WIFI:`, `geo:`, `BEGIN:VCARD`, `MECARD:`, `BEGIN:VEVENT`), AAMVA PDF417 driver's licenses, and EAN/UPC
 * product codes (ISBNs for `978`/`979` EAN-13s), else plain text. Like ML Kit, the display value is the main field
 * of structured values (the URL of a bookmark, the SSID of a WiFi config, the number of a phone/SMS, the address of
 * an email, the name of a contact, the summary of an event, the coordinates of a location), and the raw value
 * otherwise.
 */
internal object BarcodeValueParser {
    /** The prefixes of an ISBN (Bookland) EAN-13. */
    private val isbnPrefixes = listOf("978", "979")

    /** The schemes of an SMS (or MMS) message. */
    private val smsSchemes = listOf("smsto:", "sms:", "mmsto:", "mms:")

    /** The number of characters of a driver's license's raw value searched for the AAMVA header. */
    private const val AAMVA_HEADER_LENGTH = 40

    /**
     * Parses the given [rawValue] of a barcode of the given [format].
     */
    fun parse(rawValue: String?, format: BarcodeFormat): ParsedBarcodeValue {
        if (rawValue == null) return ParsedBarcodeValue(BarcodeValueType.UNKNOWN, null)
        return when {
            format.isProductFormat -> parseProduct(rawValue, format)
            format == BarcodeFormat.PDF_417 && isDriverLicense(rawValue) ->
                ParsedBarcodeValue(BarcodeValueType.DRIVER_LICENSE, rawValue)
            else -> parseContent(rawValue) ?: ParsedBarcodeValue(BarcodeValueType.TEXT, rawValue)
        }
    }

    private val BarcodeFormat.isProductFormat: Boolean
        get() = this == BarcodeFormat.EAN_13 || this == BarcodeFormat.EAN_8 ||
            this == BarcodeFormat.UPC_A || this == BarcodeFormat.UPC_E

    private fun parseProduct(rawValue: String, format: BarcodeFormat): ParsedBarcodeValue {
        val isIsbn = format == BarcodeFormat.EAN_13 && isbnPrefixes.any { rawValue.startsWith(it) }
        val type = if (isIsbn) BarcodeValueType.ISBN else BarcodeValueType.PRODUCT
        return ParsedBarcodeValue(type, rawValue)
    }

    /** Whether the [rawValue] is an AAMVA driver's license / ID card (`@`, then an `ANSI ` or `AAMVA` header). */
    private fun isDriverLicense(rawValue: String): Boolean {
        val header = rawValue.take(AAMVA_HEADER_LENGTH)
        return rawValue.startsWith("@") && (header.contains("ANSI ") || header.contains("AAMVA"))
    }

    /** Parses a structured value from its prefix, or returns `null` if it is not structured (plain text). */
    @Suppress("CyclomaticComplexMethod")
    private fun parseContent(rawValue: String): ParsedBarcodeValue? {
        val value = rawValue.trim()
        fun hasPrefix(prefix: String) = value.startsWith(prefix, ignoreCase = true)
        return when {
            hasPrefix("BEGIN:VCARD") -> parseVCard(value)
            hasPrefix("MECARD:") -> parseMeCard(value)
            hasPrefix("BEGIN:VEVENT") || hasPrefix("BEGIN:VCALENDAR") -> parseCalendarEvent(value)
            hasPrefix("WIFI:") -> parseWifi(value)
            hasPrefix("MEBKM:") -> parseBookmark(value)
            hasPrefix("URLTO:") -> parseUrlTo(value)
            hasPrefix("MATMSG:") -> parseMatMsg(value)
            hasPrefix("mailto:") -> parseMailTo(value)
            hasPrefix("tel:") -> parseTel(value)
            smsSchemes.any { hasPrefix(it) } -> parseSms(value)
            hasPrefix("geo:") -> parseGeo(value)
            isUrl(value) -> ParsedBarcodeValue(BarcodeValueType.URL, rawValue)
            else -> null
        }
    }

    /** Whether the [value] is a web URL: `http://`, `https://` or `www.`, without whitespace. */
    private fun isUrl(value: String): Boolean {
        val hasUrlPrefix = listOf("http://", "https://", "www.").any { value.startsWith(it, ignoreCase = true) }
        return hasUrlPrefix && value.none { it.isWhitespace() }
    }
}
