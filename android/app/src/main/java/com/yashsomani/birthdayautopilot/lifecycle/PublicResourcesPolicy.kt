package com.yashsomani.birthdayautopilot.lifecycle

internal object PublicResourcesPolicy {
  private val googleCloudProjectId = Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")

  fun baseUrl(projectId: String?): String? = projectId
    ?.takeIf(googleCloudProjectId::matches)
    ?.let { "https://$it.web.app" }
}
