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

/** `WIFI:S:ssid;T:WPA;P:password;H:false;;`: the SSID, or `null` (plain text) without SSID. */
internal fun parseWifi(value: String): ParsedBarcodeValue? =
    parseMeCardFields(value.substringAfter(':'))["S"]?.firstOrNull()?.let { ssid ->
        ParsedBarcodeValue(BarcodeValueType.WIFI, ssid)
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

/** `sms:number?body=message` or `SMSTO:number:message` (or `mms:`/`MMSTO:`): the number. */
internal fun parseSms(value: String): ParsedBarcodeValue {
    val number = value.substringAfter(':').substringBefore('?').substringBefore(':')
    return ParsedBarcodeValue(BarcodeValueType.SMS, percentDecode(number))
}

/**
 * `geo:latitude,longitude[,altitude][;params][?query]`: `latitude,longitude`, or `null` (plain text) if the
 * coordinates are not numbers.
 */
internal fun parseGeo(value: String): ParsedBarcodeValue? {
    val coordinates = value.substringAfter(':').substringBefore('?').substringBefore(';').split(',').map { it.trim() }
    val latitude = coordinates.getOrNull(0)?.takeIf { it.toDoubleOrNull() != null }
    val longitude = coordinates.getOrNull(1)?.takeIf { it.toDoubleOrNull() != null }
    return if (latitude != null && longitude != null) {
        ParsedBarcodeValue(BarcodeValueType.GEO, "$latitude,$longitude")
    } else {
        null
    }
}
