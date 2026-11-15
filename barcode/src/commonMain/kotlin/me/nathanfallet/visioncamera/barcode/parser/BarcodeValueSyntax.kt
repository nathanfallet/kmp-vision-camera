package me.nathanfallet.visioncamera.barcode.parser

// The syntax helpers of BarcodeValueParser: MeCard-like fields, vCard/iCalendar content lines and URI escapes.

/** The radix of a `%XX` URI escape. */
private const val HEX_RADIX = 16

/** The length of a `%XX` URI escape. */
private const val PERCENT_ESCAPE_LENGTH = 3

/**
 * Parses the `KEY:value;KEY:value;;` fields of a MeCard-like value (MECARD, WIFI, MATMSG, MEBKM), without its
 * `PREFIX:`. Values are unescaped (`\;`, `\,`, `\:`, `\\`) and keys uppercased.
 */
internal fun parseMeCardFields(body: String): Map<String, List<String>> {
    val fields = mutableMapOf<String, MutableList<String>>()
    for (field in splitUnescaped(body, ';')) {
        val separator = field.indexOf(':')
        if (separator > 0) {
            val key = field.substring(0, separator).trim().uppercase()
            fields.getOrPut(key) { mutableListOf() }.add(unescapeMeCard(field.substring(separator + 1)))
        }
    }
    return fields
}

/** Splits the [value] on the [separator]s that are not escaped with a backslash, keeping the escapes. */
private fun splitUnescaped(value: String, separator: Char): List<String> {
    val parts = mutableListOf<String>()
    val part = StringBuilder()
    var escaped = false
    for (char in value) {
        when {
            escaped -> {
                part.append(char)
                escaped = false
            }
            char == '\\' -> {
                part.append(char)
                escaped = true
            }
            char == separator -> {
                parts.add(part.toString())
                part.clear()
            }
            else -> part.append(char)
        }
    }
    parts.add(part.toString())
    return parts
}

/** Removes the backslash escapes of a MeCard-like field value. */
private fun unescapeMeCard(value: String): String {
    val result = StringBuilder()
    var escaped = false
    for (char in value) {
        escaped = if (!escaped && char == '\\') {
            true
        } else {
            result.append(char)
            false
        }
    }
    return result.toString()
}

/**
 * Parses the content lines (`NAME;PARAM=VALUE:value`) of a vCard or iCalendar value, unfolding folded lines.
 * Returns the first value of each property name (uppercased, without parameters), unescaped.
 */
internal fun parseContentLines(value: String): Map<String, String> {
    val unfolded = value.replace(Regex("\r?\n[ \t]"), "")
    val properties = mutableMapOf<String, String>()
    for (line in unfolded.split(Regex("\r?\n|\r"))) {
        val separator = line.indexOf(':')
        if (separator > 0) {
            val name = line.substring(0, separator).substringBefore(';').trim().uppercase()
            if (name !in properties) properties[name] = unescapeText(line.substring(separator + 1).trim())
        }
    }
    return properties
}

/** Unescapes a vCard/iCalendar text value (`\n`, `\,`, `\;`, `\\`). */
private fun unescapeText(value: String): String = value
    .replace("\\n", "\n")
    .replace("\\N", "\n")
    .replace("\\,", ",")
    .replace("\\;", ";")
    .replace("\\\\", "\\")

/** Decodes the `%XX` escapes of a URI component (as UTF-8), leaving invalid escapes as they are. */
internal fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val code = if (value[index] == '%' && index + PERCENT_ESCAPE_LENGTH <= value.length) {
            value.substring(index + 1, index + PERCENT_ESCAPE_LENGTH).toIntOrNull(HEX_RADIX)
        } else {
            null
        }
        if (code != null) {
            bytes.add(code.toByte())
            index += PERCENT_ESCAPE_LENGTH
        } else {
            bytes.addAll(value[index].toString().encodeToByteArray().toList())
            index++
        }
    }
    return bytes.toByteArray().decodeToString()
}
