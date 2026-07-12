package com.yashsomani.birthdayautopilot.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

internal class FirebaseAppCheckGate {
  suspend fun attest(firebaseApp: FirebaseApp): Boolean {
    val appCheck = runCatching { FirebaseAppCheck.getInstance(firebaseApp) }.getOrNull() ?: return false
    val provider = runCatching { PlayIntegrityAppCheckProviderFactory.getInstance() }.getOrNull() ?: return false
    runCatching { appCheck.installAppCheckProviderFactory(provider) }.getOrElse { return false }
    val task = runCatching { appCheck.getAppCheckToken(false) }.getOrNull() ?: return false
    return when (val result = task.awaitSanitized(APP_CHECK_TIMEOUT_MILLIS)) {
      is TaskResult.Success -> result.value.token.isNotBlank()
      is TaskResult.Failure -> false
    }
  }
}

internal sealed interface FirebaseExchangeResult {
  data class Success(
    val profile: IdentityProfile,
    val binding: NativeAccountBinding,
    val isNewUser: Boolean,
  ) : FirebaseExchangeResult

  data class Failure(
    val reason: IdentityFailure,
    val cleanup: FirebaseExchangeFailureCleanup = FirebaseExchangeFailureCleanup.NONE,
  ) : FirebaseExchangeResult
}

internal enum class FirebaseExchangeFailureCleanup {
  NONE,
  REMOVE_NEW_USER,
}

internal object FirebaseExchangeMetadataPolicy {
  fun cleanupForInvalidMetadata(isNewUser: Boolean): FirebaseExchangeFailureCleanup =
    if (isNewUser) {
      FirebaseExchangeFailureCleanup.REMOVE_NEW_USER
    } else {
      FirebaseExchangeFailureCleanup.NONE
    }
}

internal class FirebaseGoogleCredentialExchanger {
  suspend fun exchange(
    firebaseApp: FirebaseApp,
    idToken: EphemeralToken,
    expectedGoogleSubject: String,
    expectedEmail: String,
    fallbackDisplayName: String?,
  ): FirebaseExchangeResult {
    val auth = runCatching { FirebaseAuth.getInstance(firebaseApp) }.getOrNull()
      ?: return FirebaseExchangeResult.Failure(IdentityFailure.FIREBASE_UNAVAILABLE)
    val credential = runCatching {
      idToken.use { GoogleAuthProvider.getCredential(it, null) }
    }.getOrElse {
      return FirebaseExchangeResult.Failure(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    }
    val task = runCatching { auth.signInWithCredential(credential) }.getOrElse {
      return FirebaseExchangeResult.Failure(IdentityFailure.FIREBASE_UNAVAILABLE)
    }
    val result = task.awaitSanitized(FIREBASE_AUTH_TIMEOUT_MILLIS)
    if (result is TaskResult.Failure) {
      return FirebaseExchangeResult.Failure(mapAuthFailure(task.exception))
    }
    val authResult = (result as TaskResult.Success).value
    val user = authResult.user
      ?: return FirebaseExchangeResult.Failure(IdentityFailure.FIREBASE_UNAVAILABLE)
    val isNewUser = authResult.additionalUserInfo?.isNewUser == true
    val providerIdentity = user.googleProviderIdentity()
    if (
      providerIdentity == null ||
      providerIdentity.first != expectedGoogleSubject ||
      !providerIdentity.second.equals(expectedEmail, ignoreCase = true)
    ) {
      return invalidMetadataFailure(auth, isNewUser, IdentityFailure.ACCOUNT_MISMATCH)
    }
    val safeEmail = IdentityValuePolicy.email(user.email ?: expectedEmail)
      ?: run {
        return invalidMetadataFailure(
          auth,
          isNewUser,
          IdentityFailure.INVALID_GOOGLE_CREDENTIAL,
        )
      }
    val profile = IdentityProfile(
      displayEmail = safeEmail,
      displayName = IdentityValuePolicy.displayName(user.displayName ?: fallbackDisplayName),
    )
    return FirebaseExchangeResult.Success(
      profile = profile,
      binding = NativeAccountBinding(
        googleSubject = providerIdentity.first,
        email = safeEmail,
        firebaseUid = user.uid,
      ),
      isNewUser = isNewUser,
    )
  }

  private fun invalidMetadataFailure(
    auth: FirebaseAuth,
    isNewUser: Boolean,
    reason: IdentityFailure,
  ): FirebaseExchangeResult.Failure {
    val cleanup = FirebaseExchangeMetadataPolicy.cleanupForInvalidMetadata(isNewUser)
    if (cleanup == FirebaseExchangeFailureCleanup.NONE) auth.signOut()
    return FirebaseExchangeResult.Failure(reason, cleanup)
  }

  private fun mapAuthFailure(error: Exception?): IdentityFailure = when (error) {
    is FirebaseNetworkException -> IdentityFailure.NETWORK_UNAVAILABLE
    is FirebaseAuthInvalidUserException -> IdentityFailure.FIREBASE_USER_DISABLED
    is FirebaseAuthInvalidCredentialsException -> IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED
    else -> IdentityFailure.FIREBASE_UNAVAILABLE
  }
}

private const val APP_CHECK_TIMEOUT_MILLIS = 10_000L
private const val FIREBASE_AUTH_TIMEOUT_MILLIS = 30_000L

internal class FirebaseAccountBindingProvider(
  private val firebaseApp: FirebaseApp,
) {
  fun current(): NativeAccountBinding? {
    val user = runCatching { FirebaseAuth.getInstance(firebaseApp).currentUser }.getOrNull() ?: return null
    val provider = user.googleProviderIdentity() ?: return null
    val email = IdentityValuePolicy.email(provider.second) ?: return null
    return NativeAccountBinding(provider.first, email, user.uid)
  }
}

private fun FirebaseUser.googleProviderIdentity(): Pair<String, String>? {
  val identities = providerData.filter { it.providerId == GoogleAuthProvider.PROVIDER_ID }
  if (identities.size != 1) return null
  val identity = identities.single()
  val subject = identity.uid.takeIf(IdentityValuePolicy::isGoogleSubject) ?: return null
  val email = identity.email ?: email ?: return null
  return subject to email
}
