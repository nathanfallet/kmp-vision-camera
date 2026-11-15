package me.nathanfallet.visioncamera.barcode.parser

import me.nathanfallet.visioncamera.barcode.BarcodeFormat
import me.nathanfallet.visioncamera.barcode.BarcodeValueType
import kotlin.test.Test
import kotlin.test.assertEquals

class BarcodeValueParserTest {
    private fun assertParsed(
        rawValue: String?,
        expectedType: BarcodeValueType,
        expectedDisplayValue: String?,
        format: BarcodeFormat = BarcodeFormat.QR_CODE,
    ) {
        assertEquals(
            ParsedBarcodeValue(expectedType, expectedDisplayValue),
            BarcodeValueParser.parse(rawValue, format),
            "Parsing ${rawValue?.let { "\"$it\"" }} ($format)",
        )
    }

    @Test
    fun nullRawValueIsUnknown() {
        assertParsed(null, BarcodeValueType.UNKNOWN, null)
    }

    @Test
    fun plainTextIsText() {
        assertParsed("Hello, World!", BarcodeValueType.TEXT, "Hello, World!")
        assertParsed("line 1\nline 2", BarcodeValueType.TEXT, "line 1\nline 2")
        assertParsed("", BarcodeValueType.TEXT, "")
        assertParsed("ABC-12345", BarcodeValueType.TEXT, "ABC-12345", BarcodeFormat.CODE_128)
    }

    @Test
    fun urls() {
        assertParsed("https://margelo.com", BarcodeValueType.URL, "https://margelo.com")
        assertParsed("HTTP://EXAMPLE.COM/A?b=c", BarcodeValueType.URL, "HTTP://EXAMPLE.COM/A?b=c")
        assertParsed("www.google.com", BarcodeValueType.URL, "www.google.com")
        assertParsed("https://example.com", BarcodeValueType.URL, "https://example.com", BarcodeFormat.CODE_128)
        // Whitespace means it is a sentence, not a URL
        assertParsed("https://example.com is great", BarcodeValueType.TEXT, "https://example.com is great")
    }

    @Test
    fun bookmarks() {
        assertParsed("MEBKM:TITLE:Google;URL://www.google.com;;", BarcodeValueType.URL, "//www.google.com")
        assertParsed("MEBKM:URL:https\\://example.com;TITLE:Example;;", BarcodeValueType.URL, "https://example.com")
        assertParsed("URLTO:Example:https://example.com", BarcodeValueType.URL, "https://example.com")
        assertParsed("URLTO::https://example.com", BarcodeValueType.URL, "https://example.com")
    }

    @Test
    fun emails() {
        assertParsed("mailto:hello@example.com", BarcodeValueType.EMAIL, "hello@example.com")
        assertParsed("MAILTO:hello@example.com?subject=Hi&body=Hello", BarcodeValueType.EMAIL, "hello@example.com")
        assertParsed("mailto:hello%40example.com", BarcodeValueType.EMAIL, "hello@example.com")
        assertParsed("MATMSG:TO:hello@example.com;SUB:Hi;BODY:Hello;;", BarcodeValueType.EMAIL, "hello@example.com")
    }

    @Test
    fun phones() {
        assertParsed("tel:+1-555-0100", BarcodeValueType.PHONE, "+1-555-0100")
        assertParsed("TEL:0123456789", BarcodeValueType.PHONE, "0123456789")
    }

    @Test
    fun sms() {
        // Like ML Kit on Android: "SMSTO:+15550100:Bonjour" is displayed as "+15550100\nBonjour"
        assertParsed("SMSTO:+15550100:Hello there", BarcodeValueType.SMS, "+15550100\nHello there")
        assertParsed("smsto:+15550100", BarcodeValueType.SMS, "+15550100")
        assertParsed("sms:+15550100?body=Hello%20you", BarcodeValueType.SMS, "+15550100\nHello you")
        assertParsed("sms:+15550100", BarcodeValueType.SMS, "+15550100")
        assertParsed("MMSTO:+15550100:Hello", BarcodeValueType.SMS, "+15550100\nHello")
    }

    @Test
    fun wifi() {
        // Like ML Kit on Android: the SSID and the password
        assertParsed("WIFI:T:WPA;S:MyNetwork;P:password;;", BarcodeValueType.WIFI, "MyNetwork password")
        assertParsed(
            "WIFI:S:My\\;Net\\:work;T:WEP;P:pass\\;word;H:true;;",
            BarcodeValueType.WIFI,
            "My;Net:work pass;word",
        )
        assertParsed("WIFI:T:nopass;S:Open;P:;;", BarcodeValueType.WIFI, "Open")
        assertParsed("wifi:s:lowercase;;", BarcodeValueType.WIFI, "lowercase")
        // No SSID: not a WiFi config
        assertParsed("WIFI:T:WPA;P:password;;", BarcodeValueType.TEXT, "WIFI:T:WPA;P:password;;")
    }

    @Test
    fun geo() {
        // Like ML Kit on Android: the raw value
        assertParsed("geo:37.786971,-122.399677", BarcodeValueType.GEO, "geo:37.786971,-122.399677")
        val geoWithQuery = "GEO:48.8584,2.2945,330?q=Eiffel+Tower"
        assertParsed(geoWithQuery, BarcodeValueType.GEO, geoWithQuery)
        assertParsed("geo:48.8584,2.2945;u=35", BarcodeValueType.GEO, "geo:48.8584,2.2945;u=35")
        assertParsed("geo:somewhere", BarcodeValueType.TEXT, "geo:somewhere")
    }

    @Test
    fun contacts() {
        val vCard = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;John;;Dr.;\r\nFN:Dr. John Doe\r\nTEL:+15550100\r\nEND:VCARD"
        assertParsed(vCard, BarcodeValueType.CONTACT_INFO, "Dr. John Doe")
        val vCardWithoutFn = "BEGIN:VCARD\nVERSION:2.1\nN;CHARSET=UTF-8:Doe;Jane;Marie;;\nEND:VCARD"
        assertParsed(vCardWithoutFn, BarcodeValueType.CONTACT_INFO, "Jane Marie Doe")
        val meCard = "MECARD:N:Doe,John;TEL:+15550100;EMAIL:john@example.com;;"
        assertParsed(meCard, BarcodeValueType.CONTACT_INFO, "John Doe")
        assertParsed("MECARD:N:Jane;;", BarcodeValueType.CONTACT_INFO, "Jane")
    }

    @Test
    fun calendarEvents() {
        val event = "BEGIN:VEVENT\nSUMMARY:Team meeting\nDTSTART:20261115T090000Z\nDTEND:20261115T100000Z\nEND:VEVENT"
        assertParsed(event, BarcodeValueType.CALENDAR_EVENT, "Team meeting")
        val calendar = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Launch\\, v5\nEND:VEVENT\nEND:VCALENDAR"
        assertParsed(calendar, BarcodeValueType.CALENDAR_EVENT, "Launch, v5")
        val withoutEvent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR"
        assertParsed(withoutEvent, BarcodeValueType.TEXT, withoutEvent)
    }

    @Test
    fun driverLicenses() {
        // Like ML Kit: the type, last name (DCS) and first name (DAC, else DCT before a comma) of the DL/ID subfile
        val license = "@\n\u001e\rANSI 636014080001DL00310034DLDAQD1234562\nDCSFALLET\nDACNATHAN\r"
        assertParsed(license, BarcodeValueType.DRIVER_LICENSE, "DL FALLET NATHAN", BarcodeFormat.PDF_417)
        val idCard = "@\n\u001e\rANSI 636014080001ID00310034IDDAQD1234562\nDCSFALLET\nDACNATHAN\r"
        assertParsed(idCard, BarcodeValueType.DRIVER_LICENSE, "ID FALLET NATHAN", BarcodeFormat.PDF_417)
        val firstNames = "@\n\u001e\rAAMVA636014080001DL00310041DLDAQD1234562\nDCSFALLET \nDCTNATHAN,MARIE\r"
        assertParsed(firstNames, BarcodeValueType.DRIVER_LICENSE, "DL FALLET NATHAN", BarcodeFormat.PDF_417)
        // The DL subfile may come after others (here a ZC subfile), and may have no names
        val noNames = "@\n\u001e\rANSI 636014080002ZC00410005DL00460014ZCZCADLDAQD1234562\r"
        assertParsed(noNames, BarcodeValueType.DRIVER_LICENSE, "DL  ", BarcodeFormat.PDF_417)
        // A subfile past the end, a header without a jurisdiction version, or no "@" are plain text
        val tooLong = license.replace("DL00310034", "DL00310035")
        assertParsed(tooLong, BarcodeValueType.TEXT, tooLong, BarcodeFormat.PDF_417)
        val oldHeader = "@\n\u001e\rANSI 6360140101DL00290034DLDAQD1234562\nDCSFALLET\nDACNATHAN\r"
        assertParsed(oldHeader, BarcodeValueType.TEXT, oldHeader, BarcodeFormat.PDF_417)
        assertParsed(license.drop(1), BarcodeValueType.TEXT, license.drop(1), BarcodeFormat.PDF_417)
        // Only PDF417 codes are driver's licenses
        assertParsed(license, BarcodeValueType.TEXT, license, BarcodeFormat.QR_CODE)
        assertParsed("@ hello", BarcodeValueType.TEXT, "@ hello", BarcodeFormat.PDF_417)
    }

    @Test
    fun products() {
        assertParsed("9780201379624", BarcodeValueType.ISBN, "9780201379624", BarcodeFormat.EAN_13)
        assertParsed("9791032305690", BarcodeValueType.ISBN, "9791032305690", BarcodeFormat.EAN_13)
        assertParsed("4006381333931", BarcodeValueType.PRODUCT, "4006381333931", BarcodeFormat.EAN_13)
        assertParsed("96385074", BarcodeValueType.PRODUCT, "96385074", BarcodeFormat.EAN_8)
        assertParsed("036000291452", BarcodeValueType.PRODUCT, "036000291452", BarcodeFormat.UPC_A)
        assertParsed("01234565", BarcodeValueType.PRODUCT, "01234565", BarcodeFormat.UPC_E)
        // 978 is only an ISBN prefix for EAN-13s
        assertParsed("97801234", BarcodeValueType.PRODUCT, "97801234", BarcodeFormat.EAN_8)
        // Other formats are never products
        assertParsed("9780201379624", BarcodeValueType.TEXT, "9780201379624", BarcodeFormat.CODE_128)
    }

    @Test
    fun percentDecoding() {
        assertEquals("a b", percentDecode("a%20b"))
        assertEquals("é", percentDecode("%C3%A9"))
        assertEquals("100%", percentDecode("100%"))
        assertEquals("%zz", percentDecode("%zz"))
    }
}
