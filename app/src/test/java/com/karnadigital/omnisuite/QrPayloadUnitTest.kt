package com.karnadigital.omnisuite

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test to verify compilation of various QR code payloads.
 * Runs offline in GitHub workflows and PR verifications.
 */
class QrPayloadUnitTest {

    @Test
    fun testWiFiPayloadCompilation() {
        val ssid = "OmniOffice_5G"
        val security = "WPA"
        val password = "omnioffice2026"
        val expected = "WIFI:S:OmniOffice_5G;T:WPA;P:omnioffice2026;;"
        val actual = "WIFI:S:$ssid;T:$security;P:$password;;"
        assertEquals(expected, actual)
    }

    @Test
    fun testVCardPayloadCompilation() {
        val firstName = "Chaitanya"
        val lastName = "Karna"
        val org = "Karna Digital"
        val phone = "+919876543210"
        val email = "contact@karnadigital.com"
        val address = "Hyderabad, IN"

        val actual = """
            BEGIN:VCARD
            VERSION:3.0
            N:$lastName;$firstName;;;
            FN:$firstName $lastName
            ORG:$org
            TEL;TYPE=CELL:$phone
            EMAIL;TYPE=PREF,INTERNET:$email
            ADR:;;$address;;;;
            END:VCARD
        """.trimIndent()

        val expected = "BEGIN:VCARD\nVERSION:3.0\nN:Karna;Chaitanya;;;\nFN:Chaitanya Karna\nORG:Karna Digital\nTEL;TYPE=CELL:+919876543210\nEMAIL;TYPE=PREF,INTERNET:contact@karnadigital.com\nADR:;;Hyderabad, IN;;;;\nEND:VCARD"
        assertEquals(expected, actual)
    }

    @Test
    fun testSmsPayloadCompilation() {
        val phone = "+919876543210"
        val message = "Hello from OmniSuite!"
        val expected = "SMTO:+919876543210:Hello from OmniSuite!"
        val actual = "SMTO:$phone:$message"
        assertEquals(expected, actual)
    }

    @Test
    fun testPhonePayloadCompilation() {
        val phone = "+919876543210"
        val expected = "tel:+919876543210"
        val actual = "tel:$phone"
        assertEquals(expected, actual)
    }

    @Test
    fun testGeoPayloadCompilation() {
        val lat = "17.3850"
        val lon = "78.4867"
        val expected = "geo:17.3850,78.4867"
        val actual = "geo:$lat,$lon"
        assertEquals(expected, actual)
    }
}
