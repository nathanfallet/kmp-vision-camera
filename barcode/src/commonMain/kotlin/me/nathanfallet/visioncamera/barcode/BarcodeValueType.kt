package me.nathanfallet.visioncamera.barcode

/**
 * Represents the semantic type of data encoded in a [Barcode] - e.g. a URL, a phone number, or a WiFi config - or
 * [UNKNOWN] if the content type could not be classified.
 *
 * This describes **what** the barcode's content represents, not the barcode's visual format (see [BarcodeFormat]).
 */
enum class BarcodeValueType {
    /** The content type could not be classified. */
    UNKNOWN,

    /** A contact card (e.g. a vCard or a MeCard). */
    CONTACT_INFO,

    /** An email address or message. */
    EMAIL,

    /** An ISBN (book number). */
    ISBN,

    /** A phone number. */
    PHONE,

    /** A product code. */
    PRODUCT,

    /** An SMS message. */
    SMS,

    /** Plain text. */
    TEXT,

    /** A URL or bookmark. */
    URL,

    /** A WiFi network configuration. */
    WIFI,

    /** A geographic location. */
    GEO,

    /** A calendar event. */
    CALENDAR_EVENT,

    /** A driver's license or ID card (AAMVA format). */
    DRIVER_LICENSE,
}
