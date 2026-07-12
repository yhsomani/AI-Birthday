package com.yashsomani.birthdayautopilot.people

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal const val PEOPLE_PERSON_FIELDS = "names,birthdays,phoneNumbers,metadata"
internal const val PEOPLE_CONTACT_SOURCE = "READ_SOURCE_TYPE_CONTACT"

internal class PeopleRequest internal constructor(
  val uri: URI,
) {
  override fun toString(): String = "PeopleRequest(https://people.googleapis.com/<redacted>)"
}

internal sealed interface PeopleRequestBuildResult {
  data class Success(val request: PeopleRequest) : PeopleRequestBuildResult

  data class Failure(val reason: PeopleMalformedReason) : PeopleRequestBuildResult
}

internal class PeopleRequestFactory(
  private val pageSize: Int,
  phoneNormalizationRegion: String? = null,
) {
  private val normalizedPhoneRegion = phoneNormalizationRegion?.uppercase(Locale.ROOT)

  val parameterFingerprint: String = fingerprint(
    listOf(
      "endpoint=$ENDPOINT",
      "personFields=$PEOPLE_PERSON_FIELDS",
      "sources=$PEOPLE_CONTACT_SOURCE",
      "pageSize=$pageSize",
      "requestSyncToken=true",
      "sortOrder=LAST_MODIFIED_ASCENDING",
      "phoneNormalizationRegion=${normalizedPhoneRegion ?: "NONE"}",
    ).joinToString("&"),
  )

  init {
    require(pageSize in 1..1_000)
    require(
      normalizedPhoneRegion == null ||
        normalizedPhoneRegion in ISO_REGIONS,
    )
  }

  fun build(mode: PeopleSyncMode, pageToken: String?): PeopleRequestBuildResult {
    if (!validToken(pageToken)) return PeopleRequestBuildResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    if (
      mode is PeopleSyncMode.Incremental &&
      mode.parameterFingerprint != parameterFingerprint
    ) {
      return PeopleRequestBuildResult.Failure(PeopleMalformedReason.PARAMETER_MISMATCH)
    }
    val parameters = mutableListOf(
      "personFields" to PEOPLE_PERSON_FIELDS,
      "sources" to PEOPLE_CONTACT_SOURCE,
      "pageSize" to pageSize.toString(),
      "requestSyncToken" to "true",
      "sortOrder" to "LAST_MODIFIED_ASCENDING",
    )
    if (mode is PeopleSyncMode.Incremental) {
      if (!validToken(mode.syncToken) || mode.syncToken.isBlank()) {
        return PeopleRequestBuildResult.Failure(PeopleMalformedReason.PARAMETER_MISMATCH)
      }
      parameters += "syncToken" to mode.syncToken
    }
    pageToken?.let { parameters += "pageToken" to it }
    val query = parameters.joinToString("&") { (key, value) ->
      "${encode(key)}=${encode(value)}"
    }
    val uri = runCatching { URI.create("$ENDPOINT?$query") }.getOrNull()
      ?: return PeopleRequestBuildResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    if (uri.scheme != "https" || uri.host != "people.googleapis.com" || uri.path != PATH) {
      return PeopleRequestBuildResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    }
    return PeopleRequestBuildResult.Success(PeopleRequest(uri))
  }

  private fun validToken(value: String?): Boolean =
    value == null ||
      (value.length in 1..8_192 && value.none { it.isISOControl() || it.isWhitespace() })

  private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
      .replace("+", "%20")

  private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

  private companion object {
    val ISO_REGIONS = Locale.getISOCountries().toSet()
    const val PATH = "/v1/people/me/connections"
    const val ENDPOINT = "https://people.googleapis.com$PATH"
  }
}
