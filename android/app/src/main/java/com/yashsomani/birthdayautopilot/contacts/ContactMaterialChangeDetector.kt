package com.yashsomani.birthdayautopilot.contacts

enum class ContactMaterialChange {
  IDENTITY_CHANGED,
  NAME_CHANGED,
  BIRTHDAY_CHANGED,
  PHONE_CHANGED,
  SOURCE_BECAME_UNAVAILABLE,
}

data class ContactMaterialChangeDecision(
  val changes: Set<ContactMaterialChange>,
) {
  /** Name changes invalidate even when the currently rendered message is generic and unchanged. */
  val invalidatesApproval: Boolean get() = changes.isNotEmpty()
}

object ContactMaterialChangeDetector {
  fun compare(previous: NormalizedContact, current: NormalizedContact): ContactMaterialChangeDecision =
    ContactMaterialChangeDecision(
      buildSet {
        if (previous.identity != current.identity) add(ContactMaterialChange.IDENTITY_CHANGED)
        if (
          previous.displayName != current.displayName ||
          previous.safeGivenName != current.safeGivenName
        ) add(ContactMaterialChange.NAME_CHANGED)
        if (
          previous.birthday?.rule != current.birthday?.rule ||
          previous.birthday?.selectedSource != current.birthday?.selectedSource
        ) add(ContactMaterialChange.BIRTHDAY_CHANGED)
        if (previous.selectedPhone?.canonical != current.selectedPhone?.canonical) {
          add(ContactMaterialChange.PHONE_CHANGED)
        }
        if (
          (previous.readiness != ContactReadiness.UNAVAILABLE && current.readiness == ContactReadiness.UNAVAILABLE) ||
          (ContactIssue.DELETED !in previous.issues && ContactIssue.DELETED in current.issues)
        ) add(ContactMaterialChange.SOURCE_BECAME_UNAVAILABLE)
      },
    )
}
