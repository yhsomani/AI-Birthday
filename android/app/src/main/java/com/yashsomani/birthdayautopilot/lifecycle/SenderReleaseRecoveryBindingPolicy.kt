package com.yashsomani.birthdayautopilot.lifecycle

import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

internal data class SenderReleaseRecoveryBindingProof(
  val salt: String,
  val firebaseUidHash: String,
  val googleSubjectHash: String,
) {
  override fun toString(): String = "SenderReleaseRecoveryBindingProof(<redacted>)"
}

/**
 * Equality-only proof for the exact account allowed to finish a sender release after the protected
 * Room database and its key have been destroyed. The proof is deliberately domain-separated from
 * account-deletion recovery so neither recovery authority can be substituted for the other.
 */
internal object SenderReleaseRecoveryBindingPolicy {
  private const val FIREBASE_UID_DOMAIN = "SenderReleaseRecoveryFirebaseUid.v1"
  private const val GOOGLE_SUBJECT_DOMAIN = "SenderReleaseRecoveryGoogleSubject.v1"
  private const val STORED_GOOGLE_SUBJECT_DOMAIN = "GoogleSubject.v1"
  private val HASH = Regex("^[a-f0-9]{64}$")
  private val secureRandom = SecureRandom()

  fun from(account: AccountRecordEntity): SenderReleaseRecoveryBindingProof? {
    val salt = ByteArray(32).also(secureRandom::nextBytes).toHex()
    val proof = SenderReleaseRecoveryBindingProof(
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
    proof: SenderReleaseRecoveryBindingProof,
    binding: NativeAccountBinding,
  ): Boolean {
    if (!valid(proof)) return false
    val storedGoogleSubjectHash = StablePrivateId.hash(
      STORED_GOOGLE_SUBJECT_DOMAIN,
      binding.googleSubject,
    )
    val candidate = SenderReleaseRecoveryBindingProof(
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

  fun matchesAccount(
    proof: SenderReleaseRecoveryBindingProof,
    account: AccountRecordEntity,
  ): Boolean {
    if (!valid(proof)) return false
    val candidateFirebaseUidHash = StablePrivateId.hash(
      FIREBASE_UID_DOMAIN,
      proof.salt,
      account.firebaseUid,
    )
    val candidateGoogleSubjectHash = StablePrivateId.hash(
      GOOGLE_SUBJECT_DOMAIN,
      proof.salt,
      account.googleSubjectHash,
    )
    return constantTimeEquals(proof.firebaseUidHash, candidateFirebaseUidHash) &&
      constantTimeEquals(proof.googleSubjectHash, candidateGoogleSubjectHash)
  }

  fun matchesGoogleSubject(
    proof: SenderReleaseRecoveryBindingProof,
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

  fun valid(proof: SenderReleaseRecoveryBindingProof): Boolean =
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
