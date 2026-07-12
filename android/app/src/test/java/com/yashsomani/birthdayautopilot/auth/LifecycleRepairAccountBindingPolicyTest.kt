package com.yashsomani.birthdayautopilot.auth

import com.yashsomani.birthdayautopilot.people.StablePrivateId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleRepairAccountBindingPolicyTest {
  @Test
  fun `only the preexisting Google subject passes before Firebase exchange`() {
    val expectedHash = StablePrivateId.hash("GoogleSubject.v1", GOOGLE_SUBJECT)
    assertTrue(
      LifecycleRepairAccountBindingPolicy.matchesGoogleSubject(expectedHash, GOOGLE_SUBJECT),
    )
    assertFalse(
      LifecycleRepairAccountBindingPolicy.matchesGoogleSubject(expectedHash, OTHER_SUBJECT),
    )
  }

  @Test
  fun `post exchange binding requires exact preexisting Firebase uid and subject`() {
    val expectedHash = StablePrivateId.hash("GoogleSubject.v1", GOOGLE_SUBJECT)
    val accountId = StablePrivateId.prefixed("a", "FirebaseAccount.v1", FIREBASE_UID)
    assertTrue(
      LifecycleRepairAccountBindingPolicy.matchesBinding(
        accountId,
        FIREBASE_UID,
        expectedHash,
        NativeAccountBinding(GOOGLE_SUBJECT, "person@example.com", FIREBASE_UID),
      ),
    )
    assertFalse(
      LifecycleRepairAccountBindingPolicy.matchesBinding(
        accountId,
        FIREBASE_UID,
        expectedHash,
        NativeAccountBinding(GOOGLE_SUBJECT, "person@example.com", OTHER_FIREBASE_UID),
      ),
    )
    assertFalse(
      LifecycleRepairAccountBindingPolicy.matchesBinding(
        accountId,
        FIREBASE_UID,
        expectedHash,
        NativeAccountBinding(OTHER_SUBJECT, "other@example.com", FIREBASE_UID),
      ),
    )
  }

  private companion object {
    const val GOOGLE_SUBJECT = "google-subject-1"
    const val OTHER_SUBJECT = "google-subject-2"
    const val FIREBASE_UID = "firebase-uid-1"
    const val OTHER_FIREBASE_UID = "firebase-uid-2"
  }
}
