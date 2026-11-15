package me.nathanfallet.visioncamera.barcode.parser

import me.nathanfallet.visioncamera.barcode.BarcodeValueType

// The parsers of the URL, WiFi, email, phone, SMS and geo values of BarcodeValueParser. Each one receives the trimmed
// raw value, starting with its (case-insensitive) prefix.

/** `MEBKM:TITLE:title;URL:url;;`: the URL. */
internal fun parseBookmark(value: String): ParsedBarcodeValue {
    val url = parseMeCardFields(value.substringAfter(':'))["URL"]?.firstOrNull()
    return ParsedBarcodeValue(BarcodeValueType.URL, url ?: value)
}

/** `URLTO:title:url` (the title may be empty): the URL. */
internal fun parseUrlTo(value: String): ParsedBarcodeValue {
    val rest = value.substringAfter(':')
    val url = rest.substringAfter(':', missingDelimiterValue = rest)
    return ParsedBarcodeValue(BarcodeValueType.URL, url)
}

/**
 * `WIFI:S:ssid;T:WPA;P:password;H:false;;`: the SSID and the password separated by a space (like ML Kit), or `null`
 * (plain text) without SSID.
 */
internal fun parseWifi(value: String): ParsedBarcodeValue? {
    val fields = parseMeCardFields(value.substringAfter(':'))
    val ssid = fields["S"]?.firstOrNull() ?: return null
    val password = fields["P"]?.firstOrNull()?.takeIf { it.isNotEmpty() }
    return ParsedBarcodeValue(BarcodeValueType.WIFI, listOfNotNull(ssid, password).joinToString(" "))
}

/** `MATMSG:TO:address;SUB:subject;BODY:body;;`: the address. */
internal fun parseMatMsg(value: String): ParsedBarcodeValue {
    val to = parseMeCardFields(value.substringAfter(':'))["TO"]?.firstOrNull()
    return ParsedBarcodeValue(BarcodeValueType.EMAIL, to ?: value)
}

/** `mailto:address?subject=subject&body=body`: the address. */
internal fun parseMailTo(value: String): ParsedBarcodeValue {
    val address = percentDecode(value.substringAfter(':').substringBefore('?'))
    return ParsedBarcodeValue(BarcodeValueType.EMAIL, address)
}

/** `tel:number`: the number. */
internal fun parseTel(value: String): ParsedBarcodeValue {
    val number = percentDecode(value.substringAfter(':'))
    return ParsedBarcodeValue(BarcodeValueType.PHONE, number)
}

/**
 * `sms:number?body=message` or `SMSTO:number:message` (or `mms:`/`MMSTO:`): the number, and the message on a new line
 * if there is one (like ML Kit).
 */
internal fun parseSms(value: String): ParsedBarcodeValue {
    val rest = value.substringAfter(':')
    val isSmsTo = value.substringBefore(':').endsWith("to", ignoreCase = true)
    val (number, message) = if (isSmsTo) {
        rest.substringBefore(':') to rest.substringAfter(':', missingDelimiterValue = "")
    } else {
        val body = rest.substringAfter('?', missingDelimiterValue = "").split('&')
            .firstOrNull { it.startsWith("body=", ignoreCase = true) }
            ?.substringAfter('=')
            .orEmpty()
        percentDecode(rest.substringBefore('?')) to percentDecode(body.replace('+', ' '))
    }
    val displayValue = listOf(number, message).filter { it.isNotEmpty() }.joinToString("\n")
    return ParsedBarcodeValue(BarcodeValueType.SMS, displayValue)
}

/**
 * `geo:latitude,longitude[,altitude][;params][?query]`: the raw value (like ML Kit), or `null` (plain text) if the
 * coordinates are not numbers.
 */
internal fun parseGeo(value: String): ParsedBarcodeValue? {
    val coordinates = value.substringAfter(':').substringBefore('?').substringBefore(';').split(',').map { it.trim() }
    val latitude = coordinates.getOrNull(0)?.takeIf { it.toDoubleOrNull() != null }
    val longitude = coordinates.getOrNull(1)?.takeIf { it.toDoubleOrNull() != null }
    return if (latitude != null && longitude != null) {
        ParsedBarcodeValue(BarcodeValueType.GEO, value)
    } else {
        null
    }
}
