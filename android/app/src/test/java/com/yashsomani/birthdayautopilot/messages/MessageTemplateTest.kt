package com.yashsomani.birthdayautopilot.messages

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplateTest {
  private val validator = MessageTemplateValidator()

  @Test
  fun `all built-in English and Hindi variants validate deterministically offline`() {
    MessageLanguage.entries.forEach { language ->
      val personalized = validator.validateAndRender(
        BuiltInMessageTemplates.personalized(language),
        givenName = if (language == MessageLanguage.HINDI) "अनाया" else "Ada",
      )
      val generic = validator.validateAndRender(BuiltInMessageTemplates.generic(language), givenName = null)

      assertTrue("$language personalized: ${personalized.errors}", personalized.valid)
      assertTrue("$language generic: ${generic.errors}", generic.valid)
      assertEquals(1, personalized.preview?.metrics?.segmentCount)
      assertEquals(1, generic.preview?.metrics?.segmentCount)
    }
  }

  @Test
  fun `personalized mode requires exactly one unchanged placeholder and a safe given name`() {
    val cases = listOf(
      "Happy birthday!" to TemplateValidationError.PLACEHOLDER_REQUIRED,
      "Hi {firstName}, {firstName}!" to TemplateValidationError.PLACEHOLDER_COUNT_INVALID,
      "Hi {firstname}!" to TemplateValidationError.UNRESOLVED_VARIABLE,
      "Hi {lastName}!" to TemplateValidationError.UNRESOLVED_VARIABLE,
    )
    cases.forEachIndexed { index, (text, expected) ->
      val result = validator.validateAndRender(personalized(text, "case-$index"), "Ada")
      assertTrue("case $index: ${result.errors}", expected in result.errors)
      assertFalse(result.valid)
    }

    val missing = validator.validateAndRender(personalized("Hi {firstName}!"), "   ")
    assertTrue(TemplateValidationError.GIVEN_NAME_REQUIRED_OR_UNSAFE in missing.errors)
    assertNull(missing.preview)
  }

  @Test
  fun `generic fallback is explicit and never leaves name punctuation`() {
    val generic = generic("Happy birthday! Wishing you a wonderful day.")
    assertTrue(validator.validateAndRender(generic, null).valid)

    val accidentalName = generic("Happy birthday, {firstName}!")
    val result = validator.validateAndRender(accidentalName, null)
    assertTrue(TemplateValidationError.PLACEHOLDER_NOT_ALLOWED in result.errors)
    assertFalse(result.valid)
  }

  @Test
  fun `Unicode names normalize to NFC and apostrophes remain deterministic`() {
    val decomposed = "Jose\u0301"
    val result = validator.validateAndRender(personalized("Happy birthday, {firstName}!"), decomposed)
    assertTrue(result.valid)
    assertEquals("Happy birthday, José!", result.preview?.exactText)
    assertTrue(Normalizer.isNormalized(result.preview!!.exactText, Normalizer.Form.NFC))

    val apostrophe = validator.validateAndRender(personalized("Happy birthday, {firstName}!"), "O'Neil")
    assertEquals(SmsEncoding.GSM_7, apostrophe.preview?.metrics?.encoding)
  }

  @Test
  fun `control bidi invisible and placeholder-like names fail closed across a table`() {
    val unsafe = listOf(
      "Ada\u0000Lovelace",
      "Ada\nLovelace",
      "Ada\u061CLovelace",
      "Ada\u200ELovelace",
      "Ada\u202ELovelace",
      "Ada\u2067Lovelace",
      "Ada\u200BLovelace",
      "Ada\u2060Lovelace",
      "Ada\uFEFFLovelace",
      "{firstName}",
      "!!!",
      "a".repeat(101),
    )
    unsafe.forEachIndexed { index, name ->
      val result = validator.validateAndRender(personalized("Happy birthday, {firstName}!", "unsafe-$index"), name)
      assertTrue("case $index", TemplateValidationError.GIVEN_NAME_REQUIRED_OR_UNSAFE in result.errors)
      assertFalse(result.valid)
    }
  }

  @Test
  fun `unsafe template controls and bidi instructions are rejected without normalization tricks`() {
    val unsafeCodePoints = listOf(0x0000, 0x0009, 0x000A, 0x061C, 0x200E, 0x202A, 0x202E, 0x2066, 0x2069, 0x200B, 0x2060, 0xFEFF)
    unsafeCodePoints.forEach { codePoint ->
      val marker = String(Character.toChars(codePoint))
      val result = validator.validateAndRender(
        personalized("Happy birthday, {firstName}!$marker", "cp-$codePoint"),
        "Ada",
      )
      assertTrue("U+${codePoint.toString(16)}", TemplateValidationError.UNSAFE_UNICODE in result.errors)
      assertFalse(result.valid)
    }
  }

  @Test
  fun `GSM and Unicode segment boundaries count extension and surrogate units`() {
    val cases = listOf(
      "a".repeat(160) to Triple(SmsEncoding.GSM_7, 160, 1),
      "a".repeat(161) to Triple(SmsEncoding.GSM_7, 161, 2),
      "^".repeat(80) to Triple(SmsEncoding.GSM_7, 160, 1),
      "^".repeat(81) to Triple(SmsEncoding.GSM_7, 162, 2),
      "ह".repeat(70) to Triple(SmsEncoding.UNICODE, 70, 1),
      "ह".repeat(71) to Triple(SmsEncoding.UNICODE, 71, 2),
      "🎂".repeat(35) to Triple(SmsEncoding.UNICODE, 70, 1),
      "🎂".repeat(36) to Triple(SmsEncoding.UNICODE, 72, 2),
    )
    cases.forEachIndexed { index, (text, expected) ->
      val estimate = SmsEncodingEstimator.estimate(text)
      assertEquals("encoding case $index", expected.first, estimate.encoding)
      assertEquals("units case $index", expected.second, estimate.encodingUnitCount)
      assertEquals("segments case $index", expected.third, estimate.segmentCount)
    }
  }

  @Test
  fun `segment cap is never silently exceeded and preview retains exact metrics`() {
    val template = personalized("${"a".repeat(155)} {firstName}")
    val result = validator.validateAndRender(template, "Birthday")
    assertNotNull(result.preview)
    assertEquals(2, result.preview?.metrics?.segmentCount)
    assertTrue(result.valid)

    val lowered = validator.validateAndRender(template, "Birthday", segmentCap = 1)
    assertEquals(2, lowered.preview?.metrics?.segmentCount)
    assertTrue(TemplateValidationError.SEGMENT_CAP_EXCEEDED in lowered.errors)
    assertFalse(lowered.valid)

    listOf(0, 3, Int.MAX_VALUE).forEach { invalidCap ->
      assertTrue(
        TemplateValidationError.SEGMENT_CAP_INVALID in
          validator.validateAndRender(template, "Birthday", invalidCap).errors,
      )
    }
  }

  @Test
  fun `platform length calculator can supply a maintained native national-language plan`() {
    val nativeCalculator = SmsLengthCalculator { text ->
      SmsEncodingEstimate(
        encoding = SmsEncoding.GSM_7,
        characterCount = text.codePointCount(0, text.length),
        encodingUnitCount = text.codePointCount(0, text.length),
        segmentCount = 1,
      )
    }
    val nativeValidator = MessageTemplateValidator(nativeCalculator)
    val result = nativeValidator.validateAndRender(personalized("Happy birthday, {firstName}!"), "Çağla")
    assertTrue(result.valid)
    assertEquals(SmsEncoding.GSM_7, result.preview?.metrics?.encoding)
  }

  @Test
  fun `pathological template input is bounded before policy regexes and segment calculation`() {
    var calculatorCalls = 0
    val bounded = MessageTemplateValidator(
      SmsLengthCalculator { text ->
        calculatorCalls++
        SmsEncodingEstimator.estimate(text)
      },
    )
    val result = bounded.validateAndRender(generic("a".repeat(10_000)), null)
    assertTrue(TemplateValidationError.TOO_LARGE_TO_VALIDATE in result.errors)
    assertNull(result.preview)
    assertEquals(0, calculatorCalls)
  }

  @Test
  fun `URL tracking promotion sensitive claim age and mismatched language are rejected locally`() {
    val cases = listOf(
      "Visit https://example.com {firstName}" to TemplateValidationError.URL_NOT_ALLOWED,
      "Visit bit.ly/wish {firstName}" to TemplateValidationError.URL_NOT_ALLOWED,
      "Visit wishes.example {firstName}" to TemplateValidationError.URL_NOT_ALLOWED,
      "Hi {firstName} #birthday" to TemplateValidationError.TRACKING_OR_HASHTAG_NOT_ALLOWED,
      "Hi {firstName} utm_source=x" to TemplateValidationError.TRACKING_OR_HASHTAG_NOT_ALLOWED,
      "Limited offer for {firstName}" to TemplateValidationError.PROMOTIONAL_CONTENT_NOT_ALLOWED,
      "Remember when, {firstName}" to TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED,
      "Happy 30 years old, {firstName}" to TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED,
      "Happy 30th birthday, {firstName}" to TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED,
    )
    cases.forEachIndexed { index, (text, expected) ->
      val result = validator.validateAndRender(personalized(text, "policy-$index"), "Ada")
      assertTrue("case $index: ${result.errors}", expected in result.errors)
    }

    val hindiDeclaredAsEnglish = personalized("जन्मदिन मुबारक, {firstName}!")
    assertTrue(
      TemplateValidationError.LANGUAGE_MISMATCH in
        validator.validateAndRender(hindiDeclaredAsEnglish, "Ada").errors,
    )
    val englishDeclaredAsHindi = personalized("Happy birthday, {firstName}!").copy(language = MessageLanguage.HINDI)
    assertTrue(
      TemplateValidationError.LANGUAGE_MISMATCH in
        validator.validateAndRender(englishDeclaredAsHindi, "Ada").errors,
    )
    val japaneseDeclaredAsEnglish = personalized("誕生日おめでとう, {firstName}!")
    assertTrue(
      TemplateValidationError.LANGUAGE_MISMATCH in
        validator.validateAndRender(japaneseDeclaredAsEnglish, "Ada").errors,
    )
  }

  private fun personalized(text: String, version: String = "test-v1") = MessageTemplate(
    version = version,
    language = MessageLanguage.ENGLISH,
    placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
    source = TemplateSource.USER_EDITED,
    text = text,
  )

  private fun generic(text: String) = MessageTemplate(
    version = "generic-v1",
    language = MessageLanguage.ENGLISH,
    placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
    source = TemplateSource.USER_EDITED,
    text = text,
  )
}
