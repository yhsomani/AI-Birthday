package com.yashsomani.birthdayautopilot.lifecycle

import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionRecoveryBindingPolicyTest {
  @Test
  fun `exact firebase uid and google subject are required`() {
    val proof = checkNotNull(DeletionRecoveryBindingPolicy.from(account()))

    assertTrue(DeletionRecoveryBindingPolicy.matches(proof, binding()))
    assertTrue(
      DeletionRecoveryBindingPolicy.matchesGoogleSubject(proof, GOOGLE_SUBJECT),
    )
    assertFalse(
      DeletionRecoveryBindingPolicy.matches(
        proof,
        binding().copy(firebaseUid = "different-firebase-uid"),
      ),
    )
    assertFalse(
      DeletionRecoveryBindingPolicy.matches(
        proof,
        binding().copy(googleSubject = "different-google-subject"),
      ),
    )
    assertFalse(
      DeletionRecoveryBindingPolicy.matchesGoogleSubject(proof, "different-google-subject"),
    )
  }

  @Test
  fun `a fresh random salt prevents cross-device correlation`() {
    val first = checkNotNull(DeletionRecoveryBindingPolicy.from(account()))
    assertNotNull(first)
    val second = checkNotNull(DeletionRecoveryBindingPolicy.from(account()))

    assertNotEquals(first.salt, second.salt)
    assertNotEquals(first.firebaseUidHash, second.firebaseUidHash)
    assertNotEquals(first.googleSubjectHash, second.googleSubjectHash)
    assertTrue(DeletionRecoveryBindingPolicy.matches(first, binding()))
    assertTrue(DeletionRecoveryBindingPolicy.matches(second, binding()))
  }

  private fun account() = AccountRecordEntity(
    accountId = "a_${"a".repeat(64)}",
    activeSlot = 1,
    googleSubjectHash = StablePrivateId.hash("GoogleSubject.v1", GOOGLE_SUBJECT),
    firebaseUid = FIREBASE_UID,
    displayEmail = "private@example.invalid",
    localeTag = "en-IN",
    state = AccountRecordState.ACTIVE,
    revision = 1,
    createdAtMillis = 1,
    updatedAtMillis = 1,
  )

  private fun binding() = NativeAccountBinding(
    googleSubject = GOOGLE_SUBJECT,
    email = "private@example.invalid",
    firebaseUid = FIREBASE_UID,
  )

  private companion object {
    const val FIREBASE_UID = "firebase-uid-0123456789"
    const val GOOGLE_SUBJECT = "123456789012345678901"
  }
}
