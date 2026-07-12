package com.yashsomani.birthdayautopilot.auth

import com.yashsomani.birthdayautopilot.people.StablePrivateId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Equality-only checks for the preexisting account permitted to repair an unreadable journal. */
internal object LifecycleRepairAccountBindingPolicy {
  fun matchesGoogleSubject(
    expectedGoogleSubjectHash: String,
    googleSubject: String,
  ): Boolean {
    val actual = StablePrivateId.hash("GoogleSubject.v1", googleSubject)
    return MessageDigest.isEqual(
      expectedGoogleSubjectHash.toByteArray(StandardCharsets.US_ASCII),
      actual.toByteArray(StandardCharsets.US_ASCII),
    )
  }

  fun matchesBinding(
    expectedAccountId: String,
    expectedFirebaseUid: String,
    expectedGoogleSubjectHash: String,
    binding: NativeAccountBinding,
  ): Boolean =
    binding.firebaseUid == expectedFirebaseUid &&
      StablePrivateId.prefixed("a", "FirebaseAccount.v1", binding.firebaseUid) == expectedAccountId &&
      matchesGoogleSubject(expectedGoogleSubjectHash, binding.googleSubject)
}
