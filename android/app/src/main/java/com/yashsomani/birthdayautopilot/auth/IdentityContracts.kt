package com.yashsomani.birthdayautopilot.auth

/** Public identity results are deliberately credential-free and safe to bridge. */
sealed interface IdentityOutcome {
  data class SignedIn(val profile: IdentityProfile) : IdentityOutcome

  data class Failed(val reason: IdentityFailure) : IdentityOutcome
}

data class IdentityProfile(
  val displayEmail: String,
  val displayName: String?,
) {
  override fun toString(): String = "IdentityProfile(<redacted>)"
}

internal object IdentityValuePolicy {
  private val googleSubjectPattern = Regex("^[A-Za-z0-9_-]{1,256}$")

  fun isGoogleSubject(value: String): Boolean = googleSubjectPattern.matches(value)

  fun email(value: String?): String? {
    val candidate = value?.trim() ?: return null
    return candidate.takeIf {
      it.length in 3..254 &&
        it.count { char -> char == '@' } == 1 &&
        it.all { char -> char.code in 0x21..0x7e }
    }
  }

  fun displayName(value: String?): String? {
    val raw = value ?: return null
    if (raw.any(::unsafeDisplayCharacter) || hasUnpairedSurrogate(raw)) return null
    return raw.trim()
      .replace(Regex("\\s+"), " ")
      .takeIf { it.isNotBlank() && it.length <= 160 }
  }

  private fun unsafeDisplayCharacter(char: Char): Boolean =
    char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()

  private fun hasUnpairedSurrogate(value: String): Boolean {
    var index = 0
    while (index < value.length) {
      val char = value[index]
      when {
        char.isHighSurrogate() -> {
          if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return true
          index += 2
        }
        char.isLowSurrogate() -> return true
        else -> index += 1
      }
    }
    return false
  }
}

enum class IdentityFailure {
  ACTIVITY_UNAVAILABLE,
  TIER_CONFIGURATION_MISSING,
  FIREBASE_UNAVAILABLE,
  APP_CHECK_UNAVAILABLE,
  PLAY_SERVICES_UNAVAILABLE,
  USER_CANCELLED,
  GOOGLE_ACCOUNT_UNAVAILABLE,
  INVALID_GOOGLE_CREDENTIAL,
  ACCOUNT_MISMATCH,
  NETWORK_UNAVAILABLE,
  FIREBASE_USER_DISABLED,
  SECURITY_REAUTHENTICATION_REQUIRED,
  CREDENTIAL_PROVIDER_UNAVAILABLE,
  INTERNAL_FAILURE,
}

internal class EphemeralToken private constructor(
  private var value: String?,
) {
  fun <T> use(block: (String) -> T): T {
    val token = value ?: error("Ephemeral credential is no longer available")
    return block(token)
  }

  fun clear() {
    value = null
  }

  fun isPresent(): Boolean = !value.isNullOrBlank()

  override fun toString(): String = "EphemeralToken(<redacted>)"

  companion object {
    fun from(value: String?): EphemeralToken? =
      value?.takeIf { it.isNotBlank() }?.let(::EphemeralToken)
  }
}

internal data class NativeAccountBinding(
  val googleSubject: String,
  val email: String,
  val firebaseUid: String,
) {
  override fun toString(): String = "NativeAccountBinding(<redacted>)"
}

internal enum class IdentityPersistenceResult {
  ATTACHED,
  ACCOUNT_CONFLICT,
  STORAGE_FAILURE,
}

internal enum class IdentityBindingPersistenceStatus {
  PRESENT,
  ABSENT,
  UNAVAILABLE,
}

/** Native-only boundary. Credentials and raw provider responses are never accepted here. */
internal interface NativeIdentityAccountStore {
  suspend fun attach(
    binding: NativeAccountBinding,
    profile: IdentityProfile,
  ): IdentityPersistenceResult

  suspend fun bindingPersistenceStatus(
    binding: NativeAccountBinding,
  ): IdentityBindingPersistenceStatus
}
