package com.yashsomani.birthdayautopilot.auth

import com.yashsomani.birthdayautopilot.people.PeopleRequestFactory
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.IdentityAttachDecision
import com.yashsomani.birthdayautopilot.storage.database.PeopleSyncDao
import java.util.Locale

internal class RoomIdentityAccountStore(
  private val dao: PeopleSyncDao,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val localeTag: () -> String = { Locale.getDefault().toLanguageTag() },
) : NativeIdentityAccountStore {
  private val parameterFingerprint = PeopleRequestFactory(DEFAULT_PEOPLE_PAGE_SIZE)
    .parameterFingerprint

  override suspend fun attach(
    binding: NativeAccountBinding,
    profile: IdentityProfile,
  ): IdentityPersistenceResult {
    val now = nowMillis().takeIf { it >= 0 } ?: return IdentityPersistenceResult.STORAGE_FAILURE
    val safeLocale = localeTag()
      .takeIf { it.length in 2..64 && it.none(Char::isISOControl) }
      ?: "und"
    val accountId = StablePrivateId.prefixed("a", "FirebaseAccount.v1", binding.firebaseUid)
    val subjectHash = StablePrivateId.hash("GoogleSubject.v1", binding.googleSubject)
    val candidate = AccountRecordEntity(
      accountId = accountId,
      activeSlot = 1,
      googleSubjectHash = subjectHash,
      firebaseUid = binding.firebaseUid,
      displayEmail = profile.displayEmail,
      localeTag = safeLocale,
      state = AccountRecordState.ACTIVE,
      revision = 0,
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    return try {
      when (dao.attachIdentity(candidate, parameterFingerprint)) {
        IdentityAttachDecision.ATTACHED -> IdentityPersistenceResult.ATTACHED
        IdentityAttachDecision.ACCOUNT_CONFLICT -> IdentityPersistenceResult.ACCOUNT_CONFLICT
        IdentityAttachDecision.STORAGE_REJECTED -> IdentityPersistenceResult.STORAGE_FAILURE
      }
    } catch (_: RuntimeException) {
      IdentityPersistenceResult.STORAGE_FAILURE
    }
  }

  override suspend fun bindingPersistenceStatus(
    binding: NativeAccountBinding,
  ): IdentityBindingPersistenceStatus = try {
    val active = dao.activeAccount()
    val expectedAccountId = StablePrivateId.prefixed(
      "a",
      "FirebaseAccount.v1",
      binding.firebaseUid,
    )
    val expectedSubjectHash = StablePrivateId.hash("GoogleSubject.v1", binding.googleSubject)
    if (
      active?.accountId == expectedAccountId &&
      active.firebaseUid == binding.firebaseUid &&
      active.googleSubjectHash == expectedSubjectHash &&
      active.activeSlot == 1 &&
      active.state == AccountRecordState.ACTIVE
    ) {
      IdentityBindingPersistenceStatus.PRESENT
    } else {
      IdentityBindingPersistenceStatus.ABSENT
    }
  } catch (_: RuntimeException) {
    IdentityBindingPersistenceStatus.UNAVAILABLE
  }

  private companion object {
    const val DEFAULT_PEOPLE_PAGE_SIZE = 1_000
  }
}
