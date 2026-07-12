package com.yashsomani.birthdayautopilot.lifecycle

import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

internal data class DeletionRecoveryBindingProof(
  val salt: String,
  val firebaseUidHash: String,
  val googleSubjectHash: String,
) {
  override fun toString(): String = "DeletionRecoveryBindingProof(<redacted>)"
}

/** Equality-only account proof retained after the encrypted account database is erased. */
internal object DeletionRecoveryBindingPolicy {
  private const val FIREBASE_UID_DOMAIN = "DeletionRecoveryFirebaseUid.v1"
  private const val GOOGLE_SUBJECT_DOMAIN = "DeletionRecoveryGoogleSubject.v1"
  private const val STORED_GOOGLE_SUBJECT_DOMAIN = "GoogleSubject.v1"
  private val HASH = Regex("^[a-f0-9]{64}$")
  private val secureRandom = SecureRandom()

  fun from(account: AccountRecordEntity): DeletionRecoveryBindingProof? {
    val salt = ByteArray(32).also(secureRandom::nextBytes).toHex()
    val proof = DeletionRecoveryBindingProof(
      salt = salt,
      firebaseUidHash = StablePrivateId.hash(FIREBASE_UID_DOMAIN, salt, account.firebaseUid),
      googleSubjectHash = StablePrivateId.hash(
        GOOGLE_SUBJECT_DOMAIN,
        salt,
        account.googleSubjectHash,
      ),
    )
    return proof.takeIf(::valid)
  }

  fun matches(
    proof: DeletionRecoveryBindingProof,
    binding: NativeAccountBinding,
  ): Boolean {
    if (!valid(proof)) return false
    val storedGoogleSubjectHash = StablePrivateId.hash(
      STORED_GOOGLE_SUBJECT_DOMAIN,
      binding.googleSubject,
    )
    val candidate = DeletionRecoveryBindingProof(
      salt = proof.salt,
      firebaseUidHash = StablePrivateId.hash(
        FIREBASE_UID_DOMAIN,
        proof.salt,
        binding.firebaseUid,
      ),
      googleSubjectHash = StablePrivateId.hash(
        GOOGLE_SUBJECT_DOMAIN,
        proof.salt,
        storedGoogleSubjectHash,
      ),
    )
    return constantTimeEquals(proof.firebaseUidHash, candidate.firebaseUidHash) &&
      constantTimeEquals(proof.googleSubjectHash, candidate.googleSubjectHash)
  }

  fun matchesGoogleSubject(
    proof: DeletionRecoveryBindingProof,
    googleSubject: String,
  ): Boolean {
    if (!valid(proof)) return false
    val storedGoogleSubjectHash = StablePrivateId.hash(
      STORED_GOOGLE_SUBJECT_DOMAIN,
      googleSubject,
    )
    val candidate = StablePrivateId.hash(
      GOOGLE_SUBJECT_DOMAIN,
      proof.salt,
      storedGoogleSubjectHash,
    )
    return constantTimeEquals(proof.googleSubjectHash, candidate)
  }

  fun valid(proof: DeletionRecoveryBindingProof): Boolean =
    HASH.matches(proof.salt) && HASH.matches(proof.firebaseUidHash) &&
      HASH.matches(proof.googleSubjectHash)

  private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
    left.toByteArray(StandardCharsets.US_ASCII),
    right.toByteArray(StandardCharsets.US_ASCII),
  )

  private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
  }
}
