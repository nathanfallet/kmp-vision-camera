package me.nathanfallet.visioncamera.barcode.parser

import me.nathanfallet.visioncamera.barcode.BarcodeValueType

// The parsers of the contact and calendar event values of BarcodeValueParser. Each one receives the trimmed raw
// value, starting with its (case-insensitive) prefix.

/** The order of the structured name's components (`Last;First;Middle;Prefix;Suffix`) in a display name. */
@Suppress("MagicNumber")
private val structuredNameDisplayOrder = listOf(3, 1, 2, 0, 4)

/** `MECARD:N:Last,First;TEL:...;;`: the name, as `First Last`. */
internal fun parseMeCard(value: String): ParsedBarcodeValue {
    val name = parseMeCardFields(value.substringAfter(':'))["N"]?.firstOrNull()?.let { name ->
        val parts = name.split(',', limit = 2).map { it.trim() }
        if (parts.size == 2) "${parts[1]} ${parts[0]}".trim() else name
    }
    return ParsedBarcodeValue(BarcodeValueType.CONTACT_INFO, name?.takeIf { it.isNotBlank() } ?: value)
}

/** `BEGIN:VCARD`: the formatted name (`FN`), else the structured name (`N:Last;First;Middle;Prefix;Suffix`). */
internal fun parseVCard(value: String): ParsedBarcodeValue {
    val properties = parseContentLines(value)
    val formattedName = properties["FN"]?.takeIf { it.isNotBlank() }
    val name = formattedName ?: properties["N"]?.let { structuredName ->
        val parts = structuredName.split(';').map { it.trim() }
        structuredNameDisplayOrder.mapNotNull { parts.getOrNull(it) }.filter { it.isNotEmpty() }.joinToString(" ")
    }
    return ParsedBarcodeValue(BarcodeValueType.CONTACT_INFO, name?.takeIf { it.isNotBlank() } ?: value)
}

/**
 * `BEGIN:VEVENT` (or a `BEGIN:VCALENDAR` containing one): the event's summary, or `null` (plain text) if there is no
 * event.
 */
internal fun parseCalendarEvent(value: String): ParsedBarcodeValue? {
    val eventStart = value.indexOf("BEGIN:VEVENT", ignoreCase = true)
    if (eventStart < 0) return null
    val summary = parseContentLines(value.substring(eventStart))["SUMMARY"]?.takeIf { it.isNotBlank() }
    return ParsedBarcodeValue(BarcodeValueType.CALENDAR_EVENT, summary ?: value)
}
