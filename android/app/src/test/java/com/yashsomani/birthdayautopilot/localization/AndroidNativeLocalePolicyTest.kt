package com.yashsomani.birthdayautopilot.localization

import com.yashsomani.birthdayautopilot.people.PeopleRequestFactory
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidNativeLocalePolicyTest {
  @Test
  fun `provider observes runtime English to Hindi changes without changing the phone region`() {
    var deviceLocale = Locale.forLanguageTag("en-IN")
    val provider = AndroidNativeLocaleProvider { deviceLocale }

    val english = provider.current()
    deviceLocale = Locale.forLanguageTag("hi-IN")
    val hindi = provider.current()

    assertEquals("en", english.presentationLocale.language)
    assertEquals("hi", hindi.presentationLocale.language)
    assertEquals("IN", english.presentationLocale.country)
    assertEquals("IN", hindi.presentationLocale.country)
    assertEquals("IN", english.phoneRegion)
    assertEquals("IN", hindi.phoneRegion)
    assertEquals(
      PeopleRequestFactory(1_000, english.phoneRegion).parameterFingerprint,
      PeopleRequestFactory(1_000, hindi.phoneRegion).parameterFingerprint,
    )
  }

  @Test
  fun `unsupported language falls back to English while preserving a valid current region`() {
    val resolved = AndroidNativeLocalePolicy.resolve(Locale.forLanguageTag("fr-FR"))

    assertEquals("en", resolved.presentationLocale.language)
    assertEquals("FR", resolved.presentationLocale.country)
    assertEquals("FR", resolved.phoneRegion)
    assertNotEquals(
      PeopleRequestFactory(1_000, "IN").parameterFingerprint,
      PeopleRequestFactory(1_000, resolved.phoneRegion).parameterFingerprint,
    )
  }

  @Test
  fun `missing or non ISO region fails closed for phone normalization`() {
    val languageOnly = AndroidNativeLocalePolicy.resolve(Locale.forLanguageTag("hi"))
    val numericRegion = AndroidNativeLocalePolicy.resolve(Locale.forLanguageTag("fr-419"))
    val unavailable = AndroidNativeLocalePolicy.resolve(null)

    assertEquals("hi", languageOnly.presentationLocale.language)
    assertNull(languageOnly.phoneRegion)
    assertEquals("en", numericRegion.presentationLocale.language)
    assertNull(numericRegion.phoneRegion)
    assertEquals(Locale.ENGLISH, unavailable.presentationLocale)
    assertNull(unavailable.phoneRegion)
  }
}
