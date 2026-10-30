package com.auravpn
import com.auravpn.util.PersianDigits
import org.junit.Assert.assertEquals
import org.junit.Test
class PersianDigitsTest {
 @Test fun convertsAsciiDigitsAndPreservesOtherCharacters() {
  assertEquals("نسخه ۱۲۳ - v2",PersianDigits.format("نسخه 123 - v2"))
 }
 @Test fun canKeepLatinDigits() {
  assertEquals("v2.4",PersianDigits.format("v2.4",false))
 }
}
