package com.yashsomani.birthdayautopilot.contacts

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibPhoneNumberMetadataEngineTest {
  private val phoneUtil = PhoneNumberUtil.getInstance()
  private val engine = LibPhoneNumberMetadataEngine(phoneUtil)
  private val normalizer = PhoneNormalizer(engine)

  @Test
  fun `metadata mobile example becomes one canonical SMS candidate`() {
    val raw = example("IN", PhoneNumberType.MOBILE)
    val parsed = engine.analyze(raw, null) as PhoneMetadataResult.Parsed

    assertEquals(raw, parsed.e164)
    assertEquals(PhoneNumberKind.MOBILE, parsed.kind)
    assertTrue(parsed.possible)
    assertTrue(parsed.valid)
    assertFalse(parsed.emergency)
    assertFalse(parsed.shortCode)

    val resolution = normalizer.resolve(
      listOf(RawContactPhone("phone-id", raw, PhoneLabel.OTHER)),
      selectedPhoneId = null,
      homeRegion = null,
    )
    assertEquals(raw, resolution.selected?.canonical?.value)
    assertTrue(resolution.rejected.isEmpty())
  }

  @Test
  fun `fixed toll-free and premium metadata never become SMS candidates`() {
    val cases = listOf(
      example("GB", PhoneNumberType.FIXED_LINE) to PhoneRejectionReason.NOT_SMS_CAPABLE,
      example("US", PhoneNumberType.TOLL_FREE) to PhoneRejectionReason.NOT_SMS_CAPABLE,
      example("GB", PhoneNumberType.PREMIUM_RATE) to PhoneRejectionReason.PREMIUM_RATE,
    )

    cases.forEachIndexed { index, (raw, expected) ->
      val result = normalizer.resolve(
        listOf(RawContactPhone("phone-$index", raw, PhoneLabel.OTHER)),
        selectedPhoneId = null,
        homeRegion = null,
      )
      assertNull("case $index", result.selected)
      assertEquals("case $index", expected, result.rejected.single().reason)
    }
  }

  @Test
  fun `emergency and known short forms are surfaced and blocked`() {
    val localEmergency = engine.analyze("112", "IN") as PhoneMetadataResult.Parsed
    assertTrue(localEmergency.emergency)
    assertTrue(localEmergency.shortCode)

    val internationalEmergencyForm = engine.analyze("+1 911", null) as PhoneMetadataResult.Parsed
    assertTrue(internationalEmergencyForm.emergency)
    assertTrue(internationalEmergencyForm.shortCode)

    val blocked = normalizer.resolve(
      listOf(RawContactPhone("emergency", "112", PhoneLabel.OTHER)),
      selectedPhoneId = null,
      homeRegion = "IN",
    )
    assertEquals(PhoneRejectionReason.EMERGENCY_NUMBER, blocked.rejected.single().reason)
  }

  @Test
  fun `extensions remain explicit and are never discarded during E164 formatting`() {
    val mobile = example("IN", PhoneNumberType.MOBILE)
    val analyzed = engine.analyze("$mobile ext. 42", null) as PhoneMetadataResult.Parsed
    assertEquals("42", analyzed.extension)

    val blocked = normalizer.resolve(
      listOf(RawContactPhone("extension", "$mobile ext. 42", PhoneLabel.OTHER)),
      selectedPhoneId = null,
      homeRegion = null,
    )
    assertEquals(PhoneRejectionReason.EXTENSION_NOT_SUPPORTED, blocked.rejected.single().reason)
  }

  @Test
  fun `missing or unsupported region and pathological input fail closed`() {
    assertEquals(PhoneMetadataResult.Ambiguous, engine.analyze("2025550123", null))
    assertEquals(PhoneMetadataResult.Malformed, engine.analyze("2025550123", "ZZ"))
    assertEquals(PhoneMetadataResult.Malformed, engine.analyze("+" + "9".repeat(500), null))
    assertEquals(PhoneMetadataResult.Malformed, engine.analyze("not a phone", "IN"))
  }

  @Test
  fun `metadata result and canonical values redact their private payloads`() {
    val mobile = example("IN", PhoneNumberType.MOBILE)
    val result = engine.analyze(mobile, null)
    val canonical = requireNotNull(CanonicalPhoneNumber.parse(mobile))

    assertFalse(result.toString().contains(mobile))
    assertFalse(canonical.toString().contains(mobile))
  }

  private fun example(region: String, type: PhoneNumberType): String {
    val number = requireNotNull(phoneUtil.getExampleNumberForType(region, type))
    return phoneUtil.format(number, PhoneNumberFormat.E164)
  }
}
