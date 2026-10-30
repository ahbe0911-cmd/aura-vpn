package com.auravpn

import com.auravpn.util.PersianDigits
import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDigitsTest {
    @Test
    fun convertsAsciiDigitsAndPreservesNonDigitCharacters() {
        assertEquals("نسخه ۱۲۳ - v۲", PersianDigits.format("نسخه 123 - v2"))
    }

    @Test
    fun canKeepLatinDigits() {
        assertEquals("v2.4", PersianDigits.format("v2.4", false))
    }

    @Test
    fun convertsAllDigitsInMixedTextConsistently() {
        assertEquals("v۱.۲ build ۳", PersianDigits.format("v1.2 build 3"))
    }
}
