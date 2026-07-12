package com.yashsomani.birthdayautopilot.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseNetworkException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import android.accounts.Account
import kotlinx.coroutines.CancellationException

/**
 * Owns the complete native Google-ID-token -> Firebase Auth exchange. No credential-bearing
 * object is returned to React Native or any persistence layer.
 */
internal class AndroidGoogleIdentityCoordinator(
  context: Context,
  private val environment: String,
  private val activityProvider: () -> Activity?,
  private val accountStore: NativeIdentityAccountStore,
  private val appCheckGate: FirebaseAppCheckGate = FirebaseAppCheckGate(),
  private val exchanger: FirebaseGoogleCredentialExchanger = FirebaseGoogleCredentialExchanger(),
) {
  private val applicationContext = context.applicationContext
  private val credentialManager = CredentialManager.create(applicationContext)
  private val deletionRecoverySessionOutcomeGuard = DeletionRecoverySessionOutcomeGuard(
    clearSession = ::completeSignOutAfterSafetyShutdown,
  )

  suspend fun continueWithGoogle(): IdentityOutcome = try {
    continueWithGoogleInternal()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: RuntimeException) {
    IdentityOutcome.Failed(IdentityFailure.INTERNAL_FAILURE)
  }

  suspend fun continueWithGoogleForDeletionRecovery(
    matchesRecoveryGoogleSubject: (String) -> Boolean,
    matchesRecoveryBinding: (NativeAccountBinding) -> Boolean,
  ): IdentityOutcome = deletionRecoverySessionOutcomeGuard.run {
    continueWithGoogleInternal(matchesRecoveryGoogleSubject, matchesRecoveryBinding)
  }

  suspend fun continueWithGoogleForLifecycleRepair(
    matchesPreexistingGoogleSubject: (String) -> Boolean,
    matchesPreexistingBinding: (NativeAccountBinding) -> Boolean,
  ): IdentityOutcome = deletionRecoverySessionOutcomeGuard.run {
    // The predicate-bearing branch never invokes accountStore.attach. Wrong subjects are rejected
    // before Firebase, while fresh/replacement Firebase users are removed after exchange.
    continueWithGoogleInternal(matchesPreexistingGoogleSubject, matchesPreexistingBinding)
  }

  suspend fun reauthenticateExactAccount(): IdentityOutcome = try {
    reauthenticateExactAccountInternal()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: RuntimeException) {
    IdentityOutcome.Failed(IdentityFailure.INTERNAL_FAILURE)
  }

  private suspend fun reauthenticateExactAccountInternal(): IdentityOutcome {
    val activity = activityProvider()?.takeIf { it.isUsable() }
      ?: return IdentityOutcome.Failed(IdentityFailure.ACTIVITY_UNAVAILABLE)
    if (
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext) !=
      ConnectionResult.SUCCESS
    ) return IdentityOutcome.Failed(IdentityFailure.PLAY_SERVICES_UNAVAILABLE)
    val configuration = when (
      val result = AndroidIdentityConfigurationResolver(applicationContext, environment).resolve()
    ) {
      is IdentityConfigurationResult.Ready -> result.configuration
      IdentityConfigurationResult.Missing -> {
        return IdentityOutcome.Failed(IdentityFailure.TIER_CONFIGURATION_MISSING)
      }
    }
    if (!appCheckGate.attest(configuration.firebaseApp)) {
      return IdentityOutcome.Failed(IdentityFailure.APP_CHECK_UNAVAILABLE)
    }
    val before = FirebaseAccountBindingProvider(configuration.firebaseApp).current()
      ?: return IdentityOutcome.Failed(IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED)
    val auth = FirebaseAuth.getInstance(configuration.firebaseApp)
    val user = auth.currentUser
      ?: return IdentityOutcome.Failed(IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED)
    val response = when (
      val attempt = requestCredential(
        activity,
        configuration.webClientId,
        authorizedOnly = true,
        autoSelectEnabled = false,
      )
    ) {
      is CredentialAttempt.Success -> attempt.response
      CredentialAttempt.NoCredential -> {
        return IdentityOutcome.Failed(IdentityFailure.GOOGLE_ACCOUNT_UNAVAILABLE)
      }
      is CredentialAttempt.Failed -> return IdentityOutcome.Failed(attempt.reason)
    }
    val googleCredential = parseGoogleCredential(response)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    val subject = googleCredential.uniqueId
    val email = IdentityValuePolicy.email(googleCredential.email)
    if (
      subject != before.googleSubject ||
      email == null ||
      !email.equals(before.email, ignoreCase = true)
    ) return IdentityOutcome.Failed(IdentityFailure.ACCOUNT_MISMATCH)
    val token = EphemeralToken.from(googleCredential.idToken)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    return try {
      val credential = token.use { GoogleAuthProvider.getCredential(it, null) }
      val task = user.reauthenticate(credential)
      when (task.awaitSanitized(FIREBASE_REAUTH_TIMEOUT_MILLIS)) {
        is TaskResult.Failure -> IdentityOutcome.Failed(mapReauthenticationFailure(task.exception))
        is TaskResult.Success -> {
          val after = FirebaseAccountBindingProvider(configuration.firebaseApp).current()
          if (
            after == null ||
            after.firebaseUid != before.firebaseUid ||
            after.googleSubject != before.googleSubject ||
            !after.email.equals(before.email, ignoreCase = true)
          ) {
            IdentityOutcome.Failed(IdentityFailure.ACCOUNT_MISMATCH)
          } else {
            IdentityOutcome.SignedIn(
              IdentityProfile(
                displayEmail = after.email,
                displayName = IdentityValuePolicy.displayName(user.displayName),
              ),
            )
          }
        }
      }
    } finally {
      token.clear()
    }
  }

  /**
   * Revokes every Google OAuth scope granted to this app. An omitted scope list is intentional:
   * the official Authorization API interprets this account-bound request as all app access.
   */
  suspend fun revokeAllGoogleAccessAfterSafetyShutdown(): GoogleAccessRevocationOutcome {
    val configuration = AndroidIdentityConfigurationResolver(applicationContext, environment).resolve()
    if (configuration !is IdentityConfigurationResult.Ready) {
      return GoogleAccessRevocationOutcome.AMBIGUOUS
    }
    val binding = FirebaseAccountBindingProvider(configuration.configuration.firebaseApp).current()
      ?: return GoogleAccessRevocationOutcome.ACCOUNT_CHANGED
    val request = RevokeAccessRequest.builder()
      .setAccount(Account(binding.email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
      .build()
    val task = runCatching {
      Identity.getAuthorizationClient(applicationContext).revokeAccess(request)
    }.getOrElse { return GoogleAccessRevocationOutcome.AMBIGUOUS }
    if (task.awaitSanitized(FIREBASE_REAUTH_TIMEOUT_MILLIS) !is TaskResult.Success) {
      return GoogleAccessRevocationOutcome.AMBIGUOUS
    }
    runCatching { FirebaseAuth.getInstance(configuration.configuration.firebaseApp).signOut() }
      .getOrElse { return GoogleAccessRevocationOutcome.SESSION_CLEANUP_PENDING }
    return if (runCatching {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        true
      }.getOrDefault(false)
    ) {
      GoogleAccessRevocationOutcome.REVOKED
    } else {
      GoogleAccessRevocationOutcome.SESSION_CLEANUP_PENDING
    }
  }

  private suspend fun continueWithGoogleInternal(
    recoveryGoogleSubjectPredicate: ((String) -> Boolean)? = null,
    recoveryBindingPredicate: ((NativeAccountBinding) -> Boolean)? = null,
  ): IdentityOutcome {
    val activity = activityProvider().let { candidate ->
      candidate?.takeIf { it.isUsable() }
    }
      ?: return IdentityOutcome.Failed(IdentityFailure.ACTIVITY_UNAVAILABLE)
    if (
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext) !=
      ConnectionResult.SUCCESS
    ) {
      return IdentityOutcome.Failed(IdentityFailure.PLAY_SERVICES_UNAVAILABLE)
    }
    val configuration = when (
      val result = AndroidIdentityConfigurationResolver(applicationContext, environment).resolve()
    ) {
      is IdentityConfigurationResult.Ready -> result.configuration
      IdentityConfigurationResult.Missing -> {
        return IdentityOutcome.Failed(IdentityFailure.TIER_CONFIGURATION_MISSING)
      }
    }
    if (!appCheckGate.attest(configuration.firebaseApp)) {
      return IdentityOutcome.Failed(IdentityFailure.APP_CHECK_UNAVAILABLE)
    }

    val response = when (
      val authorized = requestCredential(activity, configuration.webClientId, authorizedOnly = true)
    ) {
      is CredentialAttempt.Success -> authorized.response
      CredentialAttempt.NoCredential -> when (
        val allAccounts = requestCredential(activity, configuration.webClientId, authorizedOnly = false)
      ) {
        is CredentialAttempt.Success -> allAccounts.response
        is CredentialAttempt.Failed -> return IdentityOutcome.Failed(allAccounts.reason)
        CredentialAttempt.NoCredential -> {
          return IdentityOutcome.Failed(IdentityFailure.GOOGLE_ACCOUNT_UNAVAILABLE)
        }
      }
      is CredentialAttempt.Failed -> return IdentityOutcome.Failed(authorized.reason)
    }

    val googleCredential = parseGoogleCredential(response)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    val subject = googleCredential.uniqueId.takeIf(IdentityValuePolicy::isGoogleSubject)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    val email = IdentityValuePolicy.email(googleCredential.email)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    if (
      recoveryGoogleSubjectPredicate != null &&
      !runCatching { recoveryGoogleSubjectPredicate(subject) }.getOrDefault(false)
    ) {
      runCatching { FirebaseAuth.getInstance(configuration.firebaseApp).signOut() }
      runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
      return IdentityOutcome.Failed(IdentityFailure.ACCOUNT_MISMATCH)
    }
    val token = EphemeralToken.from(googleCredential.idToken)
      ?: return IdentityOutcome.Failed(IdentityFailure.INVALID_GOOGLE_CREDENTIAL)
    return try {
      when (
        val exchanged = exchanger.exchange(
          firebaseApp = configuration.firebaseApp,
          idToken = token,
          expectedGoogleSubject = subject,
          expectedEmail = email,
          fallbackDisplayName = googleCredential.displayName,
        )
      ) {
        is FirebaseExchangeResult.Success -> if (
          recoveryGoogleSubjectPredicate != null && recoveryBindingPredicate != null
        ) {
          val exactBinding = runCatching {
            recoveryBindingPredicate(exchanged.binding)
          }.getOrDefault(false)
          when (
            DeletionRecoveryIdentityPolicy.afterFirebaseExchange(
              exactBindingMatches = exactBinding,
              isNewUser = exchanged.isNewUser,
            )
          ) {
            DeletionRecoveryPostExchangeDecision.ACCEPT_EXACT_ACCOUNT ->
              IdentityOutcome.SignedIn(exchanged.profile)
            DeletionRecoveryPostExchangeDecision.REMOVE_REPLACEMENT_USER -> {
              val removed = removeFreshFirebaseUser(configuration.firebaseApp)
              IdentityOutcome.Failed(
                if (removed) IdentityFailure.FIREBASE_USER_DISABLED
                else IdentityFailure.INTERNAL_FAILURE,
              )
            }
            DeletionRecoveryPostExchangeDecision.REJECT_EXISTING_MISMATCH -> {
              runCatching { FirebaseAuth.getInstance(configuration.firebaseApp).signOut() }
              IdentityOutcome.Failed(IdentityFailure.ACCOUNT_MISMATCH)
            }
          }
        } else {
          persistOrdinaryIdentity(configuration.firebaseApp, exchanged)
        }
        is FirebaseExchangeResult.Failure -> when (exchanged.cleanup) {
          FirebaseExchangeFailureCleanup.NONE -> IdentityOutcome.Failed(exchanged.reason)
          FirebaseExchangeFailureCleanup.REMOVE_NEW_USER -> {
            val removed = removeFreshFirebaseUser(configuration.firebaseApp)
            IdentityOutcome.Failed(
              if (removed) exchanged.reason else IdentityFailure.INTERNAL_FAILURE,
            )
          }
        }
      }
    } finally {
      token.clear()
    }
  }

  /** Must be called only after the retain/wipe policy has paused work and protected local data. */
  suspend fun completeSignOutAfterSafetyShutdown(): Boolean {
    val configuration = AndroidIdentityConfigurationResolver(applicationContext, environment).resolve()
    if (configuration !is IdentityConfigurationResult.Ready) return false
    runCatching { FirebaseAuth.getInstance(configuration.configuration.firebaseApp).signOut() }
      .getOrElse { return false }
    return runCatching {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      true
    }.getOrDefault(false)
  }

  /** Called only when Firebase proves this exchange created the user via isNewUser=true. */
  private suspend fun removeFreshFirebaseUser(
    firebaseApp: com.google.firebase.FirebaseApp,
  ): Boolean {
    val auth = FirebaseAuth.getInstance(firebaseApp)
    val user = auth.currentUser ?: return false
    val deleted = when (user.delete().awaitSanitized(FIREBASE_REAUTH_TIMEOUT_MILLIS)) {
      is TaskResult.Success -> true
      is TaskResult.Failure -> false
    }
    runCatching { auth.signOut() }
    val credentialsCleared = runCatching {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      true
    }.getOrDefault(false)
    return deleted && auth.currentUser == null && credentialsCleared
  }

  private suspend fun persistOrdinaryIdentity(
    firebaseApp: com.google.firebase.FirebaseApp,
    exchanged: FirebaseExchangeResult.Success,
  ): IdentityOutcome {
    return when (accountStore.attach(exchanged.binding, exchanged.profile)) {
      IdentityPersistenceResult.ATTACHED -> IdentityOutcome.SignedIn(exchanged.profile)
      IdentityPersistenceResult.ACCOUNT_CONFLICT -> applyFreshAttachFailureDecision(
        firebaseApp = firebaseApp,
        profile = exchanged.profile,
        failure = IdentityFailure.ACCOUNT_MISMATCH,
        decision = FreshIdentityPersistencePolicy.afterAccountConflict(exchanged.isNewUser),
      )
      IdentityPersistenceResult.STORAGE_FAILURE -> {
        val status = if (exchanged.isNewUser) {
          try {
            accountStore.bindingPersistenceStatus(exchanged.binding)
          } catch (_: RuntimeException) {
            IdentityBindingPersistenceStatus.UNAVAILABLE
          }
        } else {
          IdentityBindingPersistenceStatus.UNAVAILABLE
        }
        applyFreshAttachFailureDecision(
          firebaseApp = firebaseApp,
          profile = exchanged.profile,
          failure = IdentityFailure.INTERNAL_FAILURE,
          decision = FreshIdentityPersistencePolicy.afterStorageFailure(
            isNewUser = exchanged.isNewUser,
            persistenceStatus = status,
          ),
        )
      }
    }
  }

  private suspend fun applyFreshAttachFailureDecision(
    firebaseApp: com.google.firebase.FirebaseApp,
    profile: IdentityProfile,
    failure: IdentityFailure,
    decision: FreshIdentityAttachFailureDecision,
  ): IdentityOutcome = when (decision) {
    FreshIdentityAttachFailureDecision.ACCEPT_DURABLE_BINDING -> IdentityOutcome.SignedIn(profile)
    FreshIdentityAttachFailureDecision.REMOVE_FRESH_USER -> IdentityOutcome.Failed(
      if (removeFreshFirebaseUser(firebaseApp)) failure else IdentityFailure.INTERNAL_FAILURE,
    )
    FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY -> IdentityOutcome.Failed(
      if (clearIdentitySession(firebaseApp)) failure else IdentityFailure.INTERNAL_FAILURE,
    )
  }

  private suspend fun clearIdentitySession(
    firebaseApp: com.google.firebase.FirebaseApp,
  ): Boolean {
    val auth = FirebaseAuth.getInstance(firebaseApp)
    if (runCatching { auth.signOut() }.isFailure || auth.currentUser != null) return false
    return runCatching {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      true
    }.getOrDefault(false)
  }

  private suspend fun requestCredential(
    activity: Activity,
    webClientId: String,
    authorizedOnly: Boolean,
    autoSelectEnabled: Boolean = authorizedOnly,
  ): CredentialAttempt {
    val option = GetGoogleIdOption.Builder()
      .setServerClientId(webClientId)
      .setFilterByAuthorizedAccounts(authorizedOnly)
      .setAutoSelectEnabled(autoSelectEnabled)
      .build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(option)
      .build()
    return try {
      CredentialAttempt.Success(
        credentialManager.getCredential(
          context = activity,
          request = request,
        ),
      )
    } catch (_: NoCredentialException) {
      CredentialAttempt.NoCredential
    } catch (_: GetCredentialCancellationException) {
      CredentialAttempt.Failed(IdentityFailure.USER_CANCELLED)
    } catch (_: GetCredentialProviderConfigurationException) {
      CredentialAttempt.Failed(IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE)
    } catch (_: GetCredentialUnsupportedException) {
      CredentialAttempt.Failed(IdentityFailure.CREDENTIAL_PROVIDER_UNAVAILABLE)
    } catch (_: GetCredentialException) {
      CredentialAttempt.Failed(IdentityFailure.INTERNAL_FAILURE)
    } catch (_: RuntimeException) {
      CredentialAttempt.Failed(IdentityFailure.INTERNAL_FAILURE)
    }
  }

  private fun parseGoogleCredential(response: GetCredentialResponse): GoogleIdTokenCredential? {
    val credential = response.credential as? CustomCredential ?: return null
    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
    return try {
      GoogleIdTokenCredential.createFrom(credential.data)
    } catch (_: GoogleIdTokenParsingException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private sealed interface CredentialAttempt {
    data class Success(val response: GetCredentialResponse) : CredentialAttempt {
      override fun toString(): String = "CredentialAttempt.Success(<redacted>)"
    }

    data class Failed(val reason: IdentityFailure) : CredentialAttempt

    data object NoCredential : CredentialAttempt
  }

  private companion object {
    fun Activity.isUsable(): Boolean = !isFinishing && !isDestroyed

    const val FIREBASE_REAUTH_TIMEOUT_MILLIS = 30_000L

    fun mapReauthenticationFailure(error: Exception?): IdentityFailure = when (error) {
      is FirebaseNetworkException -> IdentityFailure.NETWORK_UNAVAILABLE
      is FirebaseAuthInvalidUserException -> IdentityFailure.FIREBASE_USER_DISABLED
      is FirebaseAuthInvalidCredentialsException -> IdentityFailure.SECURITY_REAUTHENTICATION_REQUIRED
      else -> IdentityFailure.FIREBASE_UNAVAILABLE
    }

  }
}

internal enum class GoogleAccessRevocationOutcome {
  REVOKED,
  SESSION_CLEANUP_PENDING,
  ACCOUNT_CHANGED,
  AMBIGUOUS,
}
