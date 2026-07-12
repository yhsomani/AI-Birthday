package com.yashsomani.birthdayautopilot.coordination

import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState

/** Native-only predicate; no Firebase identity value is returned to callers or JS. */
internal fun interface NativeAccountBindingPredicate {
  suspend fun matches(binding: NativeAccountBinding): Boolean
}

/**
 * Re-reads the active encrypted Room account for every preflight and compares all three independent
 * identity bindings: Firebase UID, its purpose-separated stable account ID, and the separately
 * domain-separated Google subject hash. Missing, retained, switched, or malformed accounts fail.
 */
internal class ActiveRoomAccountBindingPredicate(
  private val activeAccount: suspend () -> AccountRecordEntity?,
) : NativeAccountBindingPredicate {
  override suspend fun matches(binding: NativeAccountBinding): Boolean {
    val account = activeAccount() ?: return false
    return account.state == AccountRecordState.ACTIVE &&
      account.activeSlot == 1 &&
      binding.firebaseUid == account.firebaseUid &&
      StablePrivateId.prefixed(
        "a",
        FIREBASE_ACCOUNT_DOMAIN,
        binding.firebaseUid,
      ) == account.accountId &&
      StablePrivateId.hash(GOOGLE_SUBJECT_DOMAIN, binding.googleSubject) == account.googleSubjectHash
  }

  override fun toString(): String = "ActiveRoomAccountBindingPredicate(<redacted>)"

  private companion object {
    const val FIREBASE_ACCOUNT_DOMAIN = "FirebaseAccount.v1"
    const val GOOGLE_SUBJECT_DOMAIN = "GoogleSubject.v1"
  }
}
