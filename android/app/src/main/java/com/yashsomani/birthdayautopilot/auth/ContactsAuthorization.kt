package com.yashsomani.birthdayautopilot.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException

internal const val CONTACTS_READONLY_SCOPE = "https://www.googleapis.com/auth/contacts.readonly"

internal enum class ContactsAuthorizationFailure {
  ACTIVITY_UNAVAILABLE,
  TIER_CONFIGURATION_MISSING,
  FIREBASE_UNAVAILABLE,
  APP_CHECK_UNAVAILABLE,
  PLAY_SERVICES_UNAVAILABLE,
  FIREBASE_SESSION_MISSING,
  FOREGROUND_RESOLUTION_REQUIRED,
  USER_CANCELLED,
  PERMISSION_DENIED,
  PARTIAL_SCOPE_GRANT,
  ACCESS_TOKEN_MISSING,
  ACCOUNT_MISMATCH,
  UNEXPECTED_AUTHORIZATION_CODE,
  NETWORK_UNAVAILABLE,
  PROVIDER_UNAVAILABLE,
}

internal sealed interface ContactsAuthorizationResult {
  data class Authorized(val accessToken: EphemeralToken) : ContactsAuthorizationResult

  data class Failed(val reason: ContactsAuthorizationFailure) : ContactsAuthorizationResult
}

internal sealed interface ResolutionLaunchResult {
  data class Completed(val data: Intent) : ResolutionLaunchResult {
    override fun toString(): String = "ResolutionLaunchResult.Completed(<redacted>)"
  }

  data object Cancelled : ResolutionLaunchResult

  data object Failed : ResolutionLaunchResult
}

internal fun interface AuthorizationResolutionLauncher {
  suspend fun launch(pendingIntent: PendingIntent): ResolutionLaunchResult
}

internal object ContactsScopePolicy {
  val requestedScopes: Set<String> = setOf(CONTACTS_READONLY_SCOPE)

  fun validate(
    grantedScopes: Collection<String>,
    accessTokenPresent: Boolean,
    serverAuthCodePresent: Boolean,
  ): ContactsAuthorizationFailure? {
    if (serverAuthCodePresent) return ContactsAuthorizationFailure.UNEXPECTED_AUTHORIZATION_CODE
    val normalized = grantedScopes.map(String::trim).filter(String::isNotEmpty).toSet()
    if (CONTACTS_READONLY_SCOPE !in normalized) return ContactsAuthorizationFailure.PERMISSION_DENIED
    if (normalized != requestedScopes) return ContactsAuthorizationFailure.PARTIAL_SCOPE_GRANT
    if (!accessTokenPresent) return ContactsAuthorizationFailure.ACCESS_TOKEN_MISSING
    return null
  }
}

/** Incremental People consent. This class never asks for offline access or a server auth code. */
internal class AndroidContactsAuthorizationGateway(
  context: Context,
  private val environment: String,
  private val activityProvider: () -> Activity?,
  private val resolutionLauncher: AuthorizationResolutionLauncher?,
  private val appCheckGate: FirebaseAppCheckGate = FirebaseAppCheckGate(),
) {
  private val applicationContext = context.applicationContext

  suspend fun authorize(interactive: Boolean): ContactsAuthorizationResult = try {
    authorizeInternal(interactive)
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: RuntimeException) {
    ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.PROVIDER_UNAVAILABLE)
  }

  private suspend fun authorizeInternal(interactive: Boolean): ContactsAuthorizationResult {
    val activity = activityProvider()?.takeIf { !it.isFinishing && !it.isDestroyed }
    if (interactive && activity == null) {
      return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.ACTIVITY_UNAVAILABLE)
    }
    if (
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext) !=
      ConnectionResult.SUCCESS
    ) {
      return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.PLAY_SERVICES_UNAVAILABLE)
    }
    val configuration = when (
      val result = AndroidIdentityConfigurationResolver(applicationContext, environment).resolve()
    ) {
      is IdentityConfigurationResult.Ready -> result.configuration
      IdentityConfigurationResult.Missing -> {
        return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.TIER_CONFIGURATION_MISSING)
      }
    }
    if (!appCheckGate.attest(configuration.firebaseApp)) {
      return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.APP_CHECK_UNAVAILABLE)
    }
    val binding = FirebaseAccountBindingProvider(configuration.firebaseApp).current()
      ?: return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.FIREBASE_SESSION_MISSING)
    val client = if (activity != null) {
      Identity.getAuthorizationClient(activity)
    } else {
      Identity.getAuthorizationClient(applicationContext)
    }
    val request = AuthorizationRequest.builder()
      .setAccount(Account(binding.email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
      .setRequestedScopes(listOf(Scope(CONTACTS_READONLY_SCOPE)))
      .setOptOutIncludingGrantedScopes(true)
      .build()
    val task = client.authorize(request)
    val initial = when (val awaited = task.awaitSanitized()) {
      is TaskResult.Success -> awaited.value
      is TaskResult.Failure -> {
        val reason = when {
          awaited.category == TaskFailureCategory.CANCELLED -> {
            ContactsAuthorizationFailure.USER_CANCELLED
          }
          (task.exception as? ApiException)?.statusCode == CommonStatusCodes.NETWORK_ERROR -> {
            ContactsAuthorizationFailure.NETWORK_UNAVAILABLE
          }
          else -> ContactsAuthorizationFailure.PROVIDER_UNAVAILABLE
        }
        return ContactsAuthorizationResult.Failed(reason)
      }
    }
    val result = if (initial.hasResolution()) {
      if (!interactive || resolutionLauncher == null) {
        return ContactsAuthorizationResult.Failed(
          ContactsAuthorizationFailure.FOREGROUND_RESOLUTION_REQUIRED,
        )
      }
      val pendingIntent = initial.pendingIntent
        ?: return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.PROVIDER_UNAVAILABLE)
      when (val launched = resolutionLauncher.launch(pendingIntent)) {
        is ResolutionLaunchResult.Completed -> runCatching {
          client.getAuthorizationResultFromIntent(launched.data)
        }.getOrElse {
          return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.PERMISSION_DENIED)
        }
        ResolutionLaunchResult.Cancelled -> {
          return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.USER_CANCELLED)
        }
        ResolutionLaunchResult.Failed -> {
          return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.PROVIDER_UNAVAILABLE)
        }
      }
    } else {
      initial
    }
    return validate(result, binding)
  }

  suspend fun clear(accessToken: EphemeralToken): Boolean {
    return try {
      val client = Identity.getAuthorizationClient(applicationContext)
      val task = accessToken.use { token ->
        client.clearToken(ClearTokenRequest.builder().setToken(token).build())
      }
      task.awaitSanitized() is TaskResult.Success
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: RuntimeException) {
      false
    }
  }

  private fun validate(
    result: AuthorizationResult,
    binding: NativeAccountBinding,
  ): ContactsAuthorizationResult {
    val token = EphemeralToken.from(result.accessToken)
    val policyFailure = ContactsScopePolicy.validate(
      grantedScopes = result.grantedScopes,
      accessTokenPresent = token?.isPresent() == true,
      serverAuthCodePresent = !result.serverAuthCode.isNullOrBlank(),
    )
    if (policyFailure != null) {
      token?.clear()
      return ContactsAuthorizationResult.Failed(policyFailure)
    }
    val account = result.toGoogleSignInAccount()
    if (
      account == null ||
      account.id != binding.googleSubject ||
      !account.email.equals(binding.email, ignoreCase = true)
    ) {
      token?.clear()
      return ContactsAuthorizationResult.Failed(ContactsAuthorizationFailure.ACCOUNT_MISMATCH)
    }
    return ContactsAuthorizationResult.Authorized(token!!)
  }
}
