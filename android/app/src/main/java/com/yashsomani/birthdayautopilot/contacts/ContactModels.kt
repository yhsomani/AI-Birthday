package com.yashsomani.birthdayautopilot.contacts

import com.yashsomani.birthdayautopilot.planning.BirthdayRule

enum class GoogleContactSourceType {
  CONTACT,
  PROFILE,
  OTHER,
}

data class GoogleContactSource(
  val type: GoogleContactSourceType,
  val sourceId: String?,
) {
  override fun toString(): String = "GoogleContactSource(type=$type, sourceId=<redacted>)"
}

data class RawBirthday(
  val year: Int?,
  val month: Int?,
  val day: Int?,
) {
  override fun toString(): String = "RawBirthday(<redacted>)"
}

enum class PhoneLabel {
  MOBILE,
  FIXED_LINE,
  HOME,
  WORK,
  MAIN,
  OTHER,
}

data class RawContactPhone(
  val phoneId: String,
  val value: String,
  val label: PhoneLabel,
) {
  override fun toString(): String = "RawContactPhone(label=$label, value=<redacted>)"
}

data class RawGoogleContact(
  val localId: String,
  val resourceName: String?,
  val sources: List<GoogleContactSource>,
  val displayName: String?,
  val givenName: String?,
  val birthdays: List<RawBirthday>,
  val phones: List<RawContactPhone>,
  val deleted: Boolean,
) {
  override fun toString(): String = "RawGoogleContact(deleted=$deleted, privateFields=<redacted>)"
}

data class ContactSelections(
  val birthday: RawBirthday? = null,
  val leapDayPolicy: com.yashsomani.birthdayautopilot.planning.LeapDayPolicy? = null,
  val phoneId: String? = null,
  val homeRegion: String? = null,
)

data class StableGoogleContactIdentity(
  val resourceName: String,
  val contactSourceId: String,
) {
  override fun toString(): String = "StableGoogleContactIdentity(<redacted>)"
}

data class NormalizedBirthday(
  val rule: BirthdayRule,
  val selectedSource: RawBirthday,
) {
  override fun toString(): String = "NormalizedBirthday(<redacted>)"
}

enum class ContactReadiness {
  READY,
  NEEDS_ATTENTION,
  UNAVAILABLE,
}

enum class ContactIssue {
  DELETED,
  LOCAL_ID_INVALID,
  RESOURCE_NAME_INVALID,
  CONTACT_SOURCE_MISSING,
  CONTACT_SOURCE_CONFLICT,
  DISPLAY_NAME_UNSAFE_OR_MISSING,
  GIVEN_NAME_UNAVAILABLE,
  BIRTHDAY_MISSING,
  BIRTHDAY_INVALID,
  BIRTHDAY_SELECTION_REQUIRED,
  BIRTHDAY_SELECTION_INVALID,
  LEAP_DAY_POLICY_REQUIRED,
  LEAP_DAY_POLICY_INVALID,
  PHONE_MISSING,
  PHONE_REGION_REQUIRED,
  PHONE_AMBIGUOUS,
  PHONE_INVALID,
  PHONE_SELECTION_REQUIRED,
  PHONE_SELECTION_INVALID,
  PHONE_NOT_SMS_CAPABLE,
}

data class NormalizedContact(
  val localId: String,
  val identity: StableGoogleContactIdentity?,
  val displayName: String?,
  val safeGivenName: String?,
  val birthday: NormalizedBirthday?,
  val selectedPhone: NormalizedPhone?,
  val phoneCandidates: List<NormalizedPhone>,
  val suggestedPhoneId: String?,
  val readiness: ContactReadiness,
  val issues: Set<ContactIssue>,
) {
  override fun toString(): String = "NormalizedContact(readiness=$readiness, issues=$issues, privateFields=<redacted>)"
}
