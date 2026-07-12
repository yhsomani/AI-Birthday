package com.yashsomani.birthdayautopilot.contacts

class ContactNormalizer(
  private val birthdayNormalizer: BirthdayNormalizer,
  private val phoneNormalizer: PhoneNormalizer,
) {
  fun normalize(raw: RawGoogleContact, selections: ContactSelections = ContactSelections()): NormalizedContact {
    val issues = linkedSetOf<ContactIssue>()
    if (raw.deleted) issues += ContactIssue.DELETED
    if (!validOpaqueId(raw.localId)) issues += ContactIssue.LOCAL_ID_INVALID

    val identity = resolveIdentity(raw, issues)
    val displayName = UnicodeTextSafety.displayName(raw.displayName)
      ?: run {
        issues += ContactIssue.DISPLAY_NAME_UNSAFE_OR_MISSING
        null
      }
    val givenName = UnicodeTextSafety.smsGivenName(raw.givenName)
      ?: run {
        issues += ContactIssue.GIVEN_NAME_UNAVAILABLE
        null
      }

    val birthday = when (
      val result = birthdayNormalizer.resolve(
        raw.birthdays,
        selections.birthday,
        selections.leapDayPolicy,
      )
    ) {
      is BirthdayResolution.Resolved -> result.birthday
      is BirthdayResolution.Unresolved -> {
        issues += result.issue
        null
      }
    }

    val phones = phoneNormalizer.resolve(raw.phones, selections.phoneId, selections.homeRegion)
    issues += phoneIssues(raw.phones, phones)

    val readiness = when {
      issues.any { it in UNAVAILABLE_ISSUES } -> ContactReadiness.UNAVAILABLE
      issues.isNotEmpty() -> ContactReadiness.NEEDS_ATTENTION
      else -> ContactReadiness.READY
    }

    return NormalizedContact(
      localId = raw.localId,
      identity = identity,
      displayName = displayName,
      safeGivenName = givenName,
      birthday = birthday,
      selectedPhone = phones.selected,
      phoneCandidates = phones.candidates,
      suggestedPhoneId = phones.suggestedPhoneId,
      readiness = readiness,
      issues = issues.toSet(),
    )
  }

  private fun resolveIdentity(
    raw: RawGoogleContact,
    issues: MutableSet<ContactIssue>,
  ): StableGoogleContactIdentity? {
    val resource = raw.resourceName?.takeIf(::validResourceName)
      ?: run {
        issues += ContactIssue.RESOURCE_NAME_INVALID
        null
      }
    val contactSources = raw.sources
      .asSequence()
      .filter { it.type == GoogleContactSourceType.CONTACT }
      .mapNotNull(GoogleContactSource::sourceId)
      .filter(::validOpaqueId)
      .distinct()
      .toList()
    val source = when (contactSources.size) {
      0 -> {
        issues += ContactIssue.CONTACT_SOURCE_MISSING
        null
      }
      1 -> contactSources.single()
      else -> {
        issues += ContactIssue.CONTACT_SOURCE_CONFLICT
        null
      }
    }
    return if (resource != null && source != null) {
      StableGoogleContactIdentity(resource, source)
    } else {
      null
    }
  }

  private fun phoneIssues(
    rawPhones: List<RawContactPhone>,
    resolution: PhoneResolution,
  ): Set<ContactIssue> = buildSet {
    if (rawPhones.isEmpty()) add(ContactIssue.PHONE_MISSING)
    if (resolution.selectionRequired) add(ContactIssue.PHONE_SELECTION_REQUIRED)
    if (resolution.selectedPhoneInvalid) add(ContactIssue.PHONE_SELECTION_INVALID)
    if (resolution.selected == null && resolution.candidates.isEmpty() && rawPhones.isNotEmpty()) {
      add(ContactIssue.PHONE_INVALID)
    }
    val blockingRejections = resolution.rejected.takeIf {
      resolution.candidates.isEmpty() || resolution.selectedPhoneInvalid
    }.orEmpty()
    blockingRejections.forEach { rejected ->
      add(
        when (rejected.reason) {
          PhoneRejectionReason.REGION_REQUIRED,
          PhoneRejectionReason.REGION_INVALID,
          -> ContactIssue.PHONE_REGION_REQUIRED
          PhoneRejectionReason.AMBIGUOUS -> ContactIssue.PHONE_AMBIGUOUS
          PhoneRejectionReason.NOT_SMS_CAPABLE -> ContactIssue.PHONE_NOT_SMS_CAPABLE
          else -> ContactIssue.PHONE_INVALID
        },
      )
    }
  }

  private fun validResourceName(value: String): Boolean =
    value.startsWith("people/") &&
      value.length in 8..300 &&
      value.drop("people/".length).isNotBlank() &&
      value.none { it.isISOControl() || it.isWhitespace() }

  private companion object {
    val UNAVAILABLE_ISSUES = setOf(
      ContactIssue.DELETED,
      ContactIssue.LOCAL_ID_INVALID,
      ContactIssue.BIRTHDAY_MISSING,
      ContactIssue.BIRTHDAY_INVALID,
      ContactIssue.PHONE_MISSING,
    )

    fun validOpaqueId(value: String): Boolean =
      value.length in 1..300 && value.none { it.isISOControl() || it.isWhitespace() }
  }
}

object ContactMergePolicy {
  /** Contacts merge only with the same verified Google CONTACT source, never by name or phone. */
  fun representsSameSource(left: StableGoogleContactIdentity?, right: StableGoogleContactIdentity?): Boolean =
    left != null &&
      right != null &&
      left.contactSourceId == right.contactSourceId &&
      left.resourceName == right.resourceName
}
