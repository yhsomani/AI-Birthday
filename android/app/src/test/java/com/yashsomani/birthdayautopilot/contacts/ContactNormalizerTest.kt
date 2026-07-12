package com.yashsomani.birthdayautopilot.contacts

import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNormalizerTest {
  private val birthdayNormalizer = BirthdayNormalizer()
  private val phoneNormalizer = PhoneNormalizer { raw, _ ->
    val digits = raw.filter(Char::isDigit).takeLast(10)
    PhoneMetadataResult.Parsed(
      e164 = "+91$digits",
      kind = PhoneNumberKind.MOBILE,
      possible = digits.length == 10,
      valid = digits.length == 10,
      emergency = false,
      shortCode = digits.length < 8,
      extension = null,
    )
  }
  private val normalizer = ContactNormalizer(birthdayNormalizer, phoneNormalizer)

  @Test
  fun `ready contact requires source evidence one birthday and one chosen valid phone`() {
    val result = normalizer.normalize(baseContact())

    assertEquals(ContactReadiness.READY, result.readiness)
    assertTrue(result.issues.isEmpty())
    assertEquals("contacts/source-1", result.identity?.contactSourceId)
    assertEquals("Ada", result.safeGivenName)
    assertEquals(12, result.birthday?.rule?.month)
    assertEquals("+919876543210", result.selectedPhone?.canonical?.value)
  }

  @Test
  fun `identity is never guessed from name or phone`() {
    val missing = normalizer.normalize(
      baseContact().copy(sources = listOf(GoogleContactSource(GoogleContactSourceType.PROFILE, "profile-1"))),
    )
    assertNull(missing.identity)
    assertEquals(ContactReadiness.NEEDS_ATTENTION, missing.readiness)
    assertTrue(ContactIssue.CONTACT_SOURCE_MISSING in missing.issues)

    val conflict = normalizer.normalize(
      baseContact().copy(
        sources = listOf(
          GoogleContactSource(GoogleContactSourceType.CONTACT, "contacts/a"),
          GoogleContactSource(GoogleContactSourceType.CONTACT, "contacts/b"),
        ),
      ),
    )
    assertNull(conflict.identity)
    assertTrue(ContactIssue.CONTACT_SOURCE_CONFLICT in conflict.issues)

    val left = StableGoogleContactIdentity("people/1", "contacts/1")
    assertFalse(ContactMergePolicy.representsSameSource(left, StableGoogleContactIdentity("people/2", "contacts/1")))
    assertFalse(ContactMergePolicy.representsSameSource(left, StableGoogleContactIdentity("people/1", "contacts/2")))
    assertTrue(ContactMergePolicy.representsSameSource(left, left.copy()))
  }

  @Test
  fun `birthday normalization never infers missing fields or resolves conflicts`() {
    val invalidCases = listOf(
      listOf(RawBirthday(null, null, 10)) to ContactIssue.BIRTHDAY_INVALID,
      listOf(RawBirthday(null, 2, null)) to ContactIssue.BIRTHDAY_INVALID,
      listOf(RawBirthday(2025, 2, 29)) to ContactIssue.BIRTHDAY_INVALID,
      listOf(RawBirthday(null, 4, 31)) to ContactIssue.BIRTHDAY_INVALID,
    )
    invalidCases.forEach { (birthdays, issue) ->
      val resolution = birthdayNormalizer.resolve(birthdays, null, null)
      assertEquals(issue, (resolution as BirthdayResolution.Unresolved).issue)
    }

    val conflicting = listOf(RawBirthday(null, 7, 14), RawBirthday(1990, 7, 14))
    assertEquals(
      ContactIssue.BIRTHDAY_SELECTION_REQUIRED,
      (birthdayNormalizer.resolve(conflicting, null, null) as BirthdayResolution.Unresolved).issue,
    )
    val selected = birthdayNormalizer.resolve(conflicting, conflicting.last(), null)
    assertEquals(7, (selected as BirthdayResolution.Resolved).birthday.rule.month)
  }

  @Test
  fun `identical birthday duplicates collapse but a selection must exactly match source data`() {
    val birthday = RawBirthday(null, 7, 14)
    assertTrue(
      birthdayNormalizer.resolve(listOf(birthday, birthday.copy()), null, null) is BirthdayResolution.Resolved,
    )
    assertEquals(
      ContactIssue.BIRTHDAY_SELECTION_INVALID,
      (
        birthdayNormalizer.resolve(
          listOf(birthday),
          RawBirthday(null, 7, 15),
          null,
        ) as BirthdayResolution.Unresolved
        ).issue,
    )
  }

  @Test
  fun `February 29 requires exactly one explicit non-leap policy`() {
    val leap = listOf(RawBirthday(2000, 2, 29))
    assertEquals(
      ContactIssue.LEAP_DAY_POLICY_REQUIRED,
      (birthdayNormalizer.resolve(leap, null, null) as BirthdayResolution.Unresolved).issue,
    )
    LeapDayPolicy.entries.forEach { policy ->
      val resolved = birthdayNormalizer.resolve(leap, null, policy) as BirthdayResolution.Resolved
      assertEquals(policy, resolved.birthday.rule.leapDayPolicy)
    }
    assertEquals(
      ContactIssue.LEAP_DAY_POLICY_INVALID,
      (
        birthdayNormalizer.resolve(
          listOf(RawBirthday(null, 3, 1)),
          null,
          LeapDayPolicy.MARCH_1,
        ) as BirthdayResolution.Unresolved
        ).issue,
    )
  }

  @Test
  fun `display text strips ordinary controls while unsafe given names cannot be interpolated`() {
    assertEquals("Ada Lovelace", UnicodeTextSafety.displayName(" Ada\u0000  Lovelace "))
    assertNull(UnicodeTextSafety.smsGivenName("Ada\u0000Lovelace"))
    assertNull(UnicodeTextSafety.smsGivenName("Ada\u202ELovelace"))
    assertNull(UnicodeTextSafety.smsGivenName("{firstName}"))
    assertNull(UnicodeTextSafety.smsGivenName("a".repeat(10_000)))
    assertEquals("अनाया", UnicodeTextSafety.smsGivenName("अनाया"))

    val result = normalizer.normalize(baseContact().copy(givenName = "Ada\u202ELovelace"))
    assertEquals(ContactReadiness.NEEDS_ATTENTION, result.readiness)
    assertTrue(ContactIssue.GIVEN_NAME_UNAVAILABLE in result.issues)
  }

  @Test
  fun `deleted or incomplete contacts never become ready`() {
    val cases = listOf(
      baseContact().copy(deleted = true) to ContactIssue.DELETED,
      baseContact().copy(birthdays = emptyList()) to ContactIssue.BIRTHDAY_MISSING,
      baseContact().copy(phones = emptyList()) to ContactIssue.PHONE_MISSING,
    )
    cases.forEach { (raw, issue) ->
      val result = normalizer.normalize(raw)
      assertEquals(issue.toString(), ContactReadiness.UNAVAILABLE, result.readiness)
      assertTrue(issue.toString(), issue in result.issues)
    }
  }

  @Test
  fun `same destination and occurrence blocks every conflicting enabled contact`() {
    val shared = CanonicalPhoneNumber.parse("+919876543210")!!
    val other = CanonicalPhoneNumber.parse("+919999999999")!!
    val date = LocalDate.of(2026, 7, 14)
    val conflicts = DuplicateDestinationDetector.findConflicts(
      listOf(
        EnabledDestinationOccurrence("a", shared, date),
        EnabledDestinationOccurrence("b", shared, date),
        EnabledDestinationOccurrence("c", shared, date.plusYears(1)),
        EnabledDestinationOccurrence("d", other, date),
      ),
    )

    assertEquals(1, conflicts.size)
    assertEquals(setOf("a", "b"), conflicts.single().contactIds)
  }

  @Test
  fun `every name birthday phone identity or deletion change invalidates approval`() {
    val previous = normalizer.normalize(baseContact())
    val changes = listOf(
      normalizer.normalize(baseContact().copy(displayName = "Ada King", givenName = "Ada")) to
        ContactMaterialChange.NAME_CHANGED,
      normalizer.normalize(baseContact().copy(birthdays = listOf(RawBirthday(1815, 12, 11)))) to
        ContactMaterialChange.BIRTHDAY_CHANGED,
      normalizer.normalize(
        baseContact().copy(phones = listOf(RawContactPhone("phone-2", "+919999999999", PhoneLabel.MOBILE))),
      ) to ContactMaterialChange.PHONE_CHANGED,
      normalizer.normalize(
        baseContact().copy(
          sources = listOf(GoogleContactSource(GoogleContactSourceType.CONTACT, "contacts/source-2")),
        ),
      ) to ContactMaterialChange.IDENTITY_CHANGED,
      normalizer.normalize(baseContact().copy(deleted = true)) to ContactMaterialChange.SOURCE_BECAME_UNAVAILABLE,
    )

    changes.forEachIndexed { index, (current, expected) ->
      val decision = ContactMaterialChangeDetector.compare(previous, current)
      assertTrue("case $index: ${decision.changes}", expected in decision.changes)
      assertTrue("case $index", decision.invalidatesApproval)
    }
    assertFalse(ContactMaterialChangeDetector.compare(previous, previous.copy()).invalidatesApproval)
  }

  private fun baseContact() = RawGoogleContact(
    localId = "local-1",
    resourceName = "people/c123",
    sources = listOf(GoogleContactSource(GoogleContactSourceType.CONTACT, "contacts/source-1")),
    displayName = "Ada Lovelace",
    givenName = "Ada",
    birthdays = listOf(RawBirthday(year = 1815, month = 12, day = 10)),
    phones = listOf(RawContactPhone("phone-1", "+919876543210", PhoneLabel.MOBILE)),
    deleted = false,
  )
}
