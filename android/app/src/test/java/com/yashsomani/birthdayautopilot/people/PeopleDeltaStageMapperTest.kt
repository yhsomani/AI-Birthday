package com.yashsomani.birthdayautopilot.people

import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleDeltaStageMapperTest {
  @Test
  fun `normalizes private fields into stable opaque ids and redacted staging rows`() {
    val delta = contact(
      phone = "+91 98765 43210",
      type = "mobile",
    )
    val first = mapper("generation-one").prepare(listOf(delta), emptyMap())
    val second = mapper("generation-two").prepare(listOf(delta), emptyMap())
    val contact = first.contacts.single()
    val phone = first.phones.single()

    assertEquals("Ada Lovelace", contact.displayName)
    assertEquals(12, contact.birthdayDay)
    assertTrue(contact.contactId.matches(Regex("^c_[0-9a-f]{64}$")))
    assertTrue(phone.phoneId.matches(Regex("^p_[0-9a-f]{64}$")))
    assertEquals("+919876543210", phone.normalizedE164)
    assertEquals("•••• 3210", phone.maskedDisplay)
    assertEquals(PhoneRecordState.READY, phone.state)
    assertEquals(contact.contactId, second.contacts.single().contactId)
    assertEquals(phone.phoneId, second.phones.single().phoneId)
    assertEquals(contact.materialDigest, second.contacts.single().materialDigest)
    assertFalse(contact.toString().contains("Ada"))
    assertFalse(phone.toString().contains("98765"))
  }

  @Test
  fun `national number without a trusted region remains unavailable and unnormalized`() {
    val prepared = mapper("generation").prepare(
      listOf(contact(phone = "9876543210", type = "mobile")),
      emptyMap(),
    )
    val phone = prepared.phones.single()

    assertEquals(PhoneRecordState.NEEDS_REGION, phone.state)
    assertNull(phone.normalizedE164)
    assertNull(phone.destinationFingerprint)
    assertEquals("•••• 3210", phone.maskedDisplay)
  }

  @Test
  fun `national number uses the current runtime region supplied for this sync`() {
    val prepared = mapper("generation", homeRegion = "IN").prepare(
      listOf(contact(phone = "9876543210", type = "mobile")),
      emptyMap(),
    )
    val phone = prepared.phones.single()

    assertEquals(PhoneRecordState.READY, phone.state)
    assertEquals("+919876543210", phone.normalizedE164)
    assertEquals("IN", phone.regionCode)
  }

  @Test
  fun `incremental deletion becomes a provider-value-free tombstone`() {
    val delta = PeopleContactDelta(
      resourceName = "people/abc",
      contactSourceId = "contacts/abc",
      deleted = true,
      names = emptyList(),
      birthdays = emptyList(),
      phoneNumbers = emptyList(),
    )
    val prepared = mapper("generation").prepare(listOf(delta), emptyMap())
    val contact = prepared.contacts.single()

    assertTrue(contact.deleted)
    assertEquals("UNAVAILABLE", contact.readiness)
    assertTrue(prepared.phones.isEmpty())
    assertFalse(contact.toString().contains("people/abc"))
  }

  private fun mapper(
    generation: String,
    homeRegion: String? = null,
  ) = PeopleDeltaStageMapper(
    accountId = "a_${"1".repeat(64)}",
    generationId = generation,
    homeRegion = homeRegion,
    stagedAtMillis = 1_000,
  )

  private fun contact(phone: String, type: String) = PeopleContactDelta(
    resourceName = "people/abc",
    contactSourceId = "contacts/abc",
    deleted = false,
    names = listOf(PeopleName("Ada Lovelace", "Ada")),
    birthdays = listOf(PeopleBirthday(1815, 12, 12)),
    phoneNumbers = listOf(PeoplePhone(phone, type)),
  )
}
