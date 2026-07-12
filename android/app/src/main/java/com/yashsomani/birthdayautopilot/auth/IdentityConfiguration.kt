package com.yashsomani.birthdayautopilot.auth

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.FirebaseApp

internal data class IdentityConfiguration(
  val environment: String,
  val applicationId: String,
  val webClientId: String,
  val firebaseApp: FirebaseApp,
) {
  override fun toString(): String = "IdentityConfiguration(environment=$environment, values=<redacted>)"
}

internal sealed interface IdentityConfigurationResult {
  data class Ready(val configuration: IdentityConfiguration) : IdentityConfigurationResult

  data object Missing : IdentityConfigurationResult
}

internal object IdentityTierPolicy {
  private val expectedApplicationIds = mapOf(
    "dev" to "com.yashsomani.birthdayautopilot.dev",
    "staging" to "com.yashsomani.birthdayautopilot.staging",
    "lab" to "com.yashsomani.birthdayautopilot.lab",
    "prod" to "com.yashsomani.birthdayautopilot",
  )
  private val webClientIdPattern = Regex("^[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com$")
  private val googleAppIdPattern = Regex("^1:[0-9]+:android:[0-9a-fA-F]+$")

  fun accepts(
    environment: String,
    applicationId: String,
    webClientId: String?,
    googleAppId: String?,
    firebaseProjectId: String?,
  ): Boolean =
    expectedApplicationIds[environment] == applicationId &&
      webClientId != null &&
      webClientIdPattern.matches(webClientId) &&
      googleAppId != null &&
      googleAppIdPattern.matches(googleAppId) &&
      !firebaseProjectId.isNullOrBlank() &&
      firebaseProjectId.length <= 128 &&
      firebaseProjectId.none(Char::isWhitespace)
}

internal class AndroidIdentityConfigurationResolver(
  private val context: Context,
  private val environment: String,
) {
  fun resolve(): IdentityConfigurationResult {
    val firebaseApp = FirebaseApp.getApps(context)
      .singleOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
      ?: return IdentityConfigurationResult.Missing
    val webClientId = stringResource("default_web_client_id")
    val googleAppId = stringResource("google_app_id")
    if (
      !IdentityTierPolicy.accepts(
        environment = environment,
        applicationId = context.packageName,
        webClientId = webClientId,
        googleAppId = googleAppId,
        firebaseProjectId = firebaseApp.options.projectId,
      ) || firebaseApp.options.applicationId != googleAppId
    ) {
      return IdentityConfigurationResult.Missing
    }
    return IdentityConfigurationResult.Ready(
      IdentityConfiguration(
        environment = environment,
        applicationId = context.packageName,
        webClientId = webClientId!!,
        firebaseApp = firebaseApp,
      ),
    )
  }

  @SuppressLint("DiscouragedApi") // These resources exist only when the validated tier plugin is applied.
  private fun stringResource(name: String): String? {
    val resourceId = context.resources.getIdentifier(name, "string", context.packageName)
    if (resourceId == 0) return null
    return runCatching { context.getString(resourceId).trim() }
      .getOrNull()
      ?.takeIf { it.isNotBlank() && !it.contains("YOUR_", ignoreCase = true) }
  }
}
