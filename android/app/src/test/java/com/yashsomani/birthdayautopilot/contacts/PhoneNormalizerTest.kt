package com.yashsomani.birthdayautopilot.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {
  @Test
  fun `requires an explicit home region for a national number but not an international number`() {
    val seenRegions = mutableListOf<String?>()
    val normalizer = PhoneNormalizer { _, region ->
      seenRegions += region
      validParsed("+919876543210")
    }

    val national = normalizer.resolve(listOf(phone("1", "9876543210")), null, null)
    assertEquals(PhoneRejectionReason.REGION_REQUIRED, national.rejected.single().reason)
    assertTrue(seenRegions.isEmpty())

    val india = normalizer.resolve(listOf(phone("1", "9876543210")), null, "in")
    assertEquals("+919876543210", india.selected?.canonical?.value)
    assertEquals(listOf("IN"), seenRegions)

    val international = normalizer.resolve(listOf(phone("2", "+447911123456")), null, null)
    assertEquals("+919876543210", international.selected?.canonical?.value)
    assertEquals(listOf("IN", null), seenRegions)
  }

  @Test
  fun `blocks every unsafe phone classification fail closed`() {
    val cases = listOf(
      validParsed("+919876543210").copy(extension = "7") to PhoneRejectionReason.EXTENSION_NOT_SUPPORTED,
      validParsed("+919876543210").copy(emergency = true) to PhoneRejectionReason.EMERGENCY_NUMBER,
      validParsed("+919876543210").copy(shortCode = true) to PhoneRejectionReason.SHORT_CODE,
      validParsed("+919876543210").copy(kind = PhoneNumberKind.PREMIUM_RATE) to PhoneRejectionReason.PREMIUM_RATE,
      validParsed("+919876543210").copy(possible = false) to PhoneRejectionReason.NOT_VALID,
      validParsed("+919876543210").copy(valid = false) to PhoneRejectionReason.NOT_VALID,
      validParsed("+919876543210").copy(kind = PhoneNumberKind.FIXED_LINE) to PhoneRejectionReason.NOT_SMS_CAPABLE,
      validParsed("123") to PhoneRejectionReason.MALFORMED,
    )

    cases.forEachIndexed { index, (metadata, expected) ->
      val result = PhoneNormalizer { _, _ -> metadata }
        .resolve(listOf(phone("p-$index", "+919876543210")), null, null)
      assertNull("case $index", result.selected)
      assertEquals("case $index", expected, result.rejected.single().reason)
    }
  }

  @Test
  fun `fixed line source label is never upgraded into an SMS candidate`() {
    var calls = 0
    val result = PhoneNormalizer { _, _ ->
      calls++
      validParsed("+919876543210")
    }.resolve(
      listOf(phone("landline", "+919876543210", PhoneLabel.FIXED_LINE)),
      null,
      null,
    )

    assertEquals(0, calls)
    assertEquals(PhoneRejectionReason.NOT_SMS_CAPABLE, result.rejected.single().reason)
  }

  @Test
  fun `obvious extension syntax and invalid region codes are rejected before normalization`() {
    var calls = 0
    val normalizer = PhoneNormalizer { _, _ ->
      calls++
      validParsed("+919876543210")
    }
    listOf("ext 12", "+919876543210 x12", "+919876543210;ext=12", "+919876543210 #12").forEach { raw ->
      val result = normalizer.resolve(listOf(phone("id", raw)), null, null)
      assertEquals(PhoneRejectionReason.EXTENSION_NOT_SUPPORTED, result.rejected.single().reason)
    }
    val invalidRegion = normalizer.resolve(listOf(phone("id", "9876543210")), null, "ZZ")
    assertEquals(PhoneRejectionReason.REGION_INVALID, invalidRegion.rejected.single().reason)
    assertEquals(0, calls)
  }

  @Test
  fun `pathological raw phone input is bounded before the metadata engine`() {
    var calls = 0
    val result = PhoneNormalizer { _, _ ->
      calls++
      validParsed("+919876543210")
    }.resolve(listOf(phone("id", "+" + "9".repeat(10_000))), null, null)
    assertEquals(PhoneRejectionReason.MALFORMED, result.rejected.single().reason)
    assertEquals(0, calls)
  }

  @Test
  fun `multiple destinations require selection and mobile is only a suggestion`() {
    val normalizer = PhoneNormalizer { raw, _ ->
      when (raw) {
        "+919876543210" -> validParsed(raw, PhoneNumberKind.MOBILE)
        else -> validParsed("+447911123456", PhoneNumberKind.FIXED_LINE_OR_MOBILE)
      }
    }
    val phones = listOf(
      phone("mobile", "+919876543210", PhoneLabel.MOBILE),
      phone("other", "+447911123456", PhoneLabel.OTHER),
    )

    val unresolved = normalizer.resolve(phones, selectedPhoneId = null, homeRegion = null)
    assertTrue(unresolved.selectionRequired)
    assertNull(unresolved.selected)
    assertEquals("mobile", unresolved.suggestedPhoneId)

    val selected = normalizer.resolve(phones, selectedPhoneId = "other", homeRegion = null)
    assertFalse(selected.selectionRequired)
    assertEquals("+447911123456", selected.selected?.canonical?.value)
  }

  @Test
  fun `duplicate raw representations collapse only after the metadata engine proves one E164 destination`() {
    val result = PhoneNormalizer { _, _ -> validParsed("+919876543210") }
      .resolve(
        listOf(
          phone("formatted", "+91 98765 43210"),
          phone("compact", "+919876543210", PhoneLabel.MOBILE),
        ),
        selectedPhoneId = null,
        homeRegion = null,
      )

    assertEquals(1, result.candidates.size)
    assertEquals(setOf("compact", "formatted"), result.selected?.sourcePhoneIds)
    assertEquals("•••• 3210", result.selected?.maskedDisplay)
  }

  @Test
  fun `invalid selection and metadata failures do not fall back to another number`() {
    val normalizer = PhoneNormalizer { raw, _ ->
      when (raw) {
        "+919876543210" -> validParsed(raw)
        "+919999999999" -> PhoneMetadataResult.Malformed
        else -> throw IllegalStateException("metadata unavailable")
      }
    }
    val invalidSelection = normalizer.resolve(
      listOf(phone("good", "+919876543210"), phone("bad", "+919999999999")),
      selectedPhoneId = "bad",
      homeRegion = null,
    )
    assertTrue(invalidSelection.selectedPhoneInvalid)
    assertNull(invalidSelection.selected)

    val engineFailure = normalizer.resolve(listOf(phone("error", "+918888888888")), null, null)
    assertEquals(PhoneRejectionReason.ENGINE_UNAVAILABLE, engineFailure.rejected.single().reason)
  }

  @Test
  fun `E164 boundary validation rejects zero country code and out of range lengths`() {
    val valid = listOf("+6831234", "+12345678", "+123456789012345")
    val invalid = listOf("12345678", "+02345678", "+1", "+1234567890123456", "+12 345678")
    valid.forEach { assertEquals(it, CanonicalPhoneNumber.parse(it)?.value) }
    invalid.forEach { assertNull(it, CanonicalPhoneNumber.parse(it)) }
    assertFalse(CanonicalPhoneNumber.parse("+919876543210").toString().contains("9876543210"))
  }

  private fun phone(id: String, value: String, label: PhoneLabel = PhoneLabel.OTHER) =
    RawContactPhone(id, value, label)

  private fun validParsed(
    e164: String,
    kind: PhoneNumberKind = PhoneNumberKind.MOBILE,
  ) = PhoneMetadataResult.Parsed(
    e164 = e164,
    kind = kind,
    possible = true,
    valid = true,
    emergency = false,
    shortCode = false,
    extension = null,
  )
}
