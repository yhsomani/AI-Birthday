package com.yashsomani.birthdayautopilot.people

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.yashsomani.birthdayautopilot.auth.EphemeralToken
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface PeopleTransportResult {
  data class Success(val body: ByteArray) : PeopleTransportResult

  data object Unauthorized : PeopleTransportResult

  data object Forbidden : PeopleTransportResult

  data class RateLimited(val retryAfterSeconds: Long?) : PeopleTransportResult

  data object ExpiredSyncToken : PeopleTransportResult

  data object Offline : PeopleTransportResult

  data object Timeout : PeopleTransportResult

  data object NetworkFailure : PeopleTransportResult

  data object PageTooLarge : PeopleTransportResult

  data object UnexpectedContentType : PeopleTransportResult

  data class HttpFailure(val statusCode: Int) : PeopleTransportResult
}

internal fun interface NetworkAvailability {
  fun isOnline(): Boolean
}

internal fun interface PeopleTransport {
  suspend fun execute(request: PeopleRequest, accessToken: EphemeralToken): PeopleTransportResult
}

internal class AndroidNetworkAvailability(context: Context) : NetworkAvailability {
  private val connectivityManager = context.applicationContext
    .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  override fun isOnline(): Boolean {
    val manager = connectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }
}

internal class PeopleHttpTransport(
  private val networkAvailability: NetworkAvailability,
  private val maxPageBytes: Int,
  private val connectTimeoutMillis: Int = 10_000,
  private val readTimeoutMillis: Int = 15_000,
) : PeopleTransport {
  init {
    require(maxPageBytes > 0)
    require(connectTimeoutMillis in 1_000..30_000)
    require(readTimeoutMillis in 1_000..30_000)
  }

  override suspend fun execute(
    request: PeopleRequest,
    accessToken: EphemeralToken,
  ): PeopleTransportResult = withContext(Dispatchers.IO) {
    ensureActive()
    if (!networkAvailability.isOnline()) return@withContext PeopleTransportResult.Offline
    val connection = runCatching { request.uri.toURL().openConnection() as? HttpsURLConnection }
      .getOrNull()
      ?: return@withContext PeopleTransportResult.NetworkFailure
    try {
      if (
        connection.url.protocol != "https" ||
        connection.url.host != "people.googleapis.com" ||
        connection.url.path != "/v1/people/me/connections"
      ) {
        return@withContext PeopleTransportResult.NetworkFailure
      }
      connection.requestMethod = "GET"
      connection.instanceFollowRedirects = false
      connection.useCaches = false
      connection.doOutput = false
      connection.connectTimeout = connectTimeoutMillis
      connection.readTimeout = readTimeoutMillis
      connection.setRequestProperty("Accept", "application/json")
      accessToken.use { token -> connection.setRequestProperty("Authorization", "Bearer $token") }
      val status = connection.responseCode
      when (status) {
        HttpURLConnection.HTTP_OK -> readSuccess(connection)
        HttpURLConnection.HTTP_UNAUTHORIZED -> PeopleTransportResult.Unauthorized
        HttpURLConnection.HTTP_FORBIDDEN -> PeopleTransportResult.Forbidden
        429 -> PeopleTransportResult.RateLimited(
          PeopleHttpResponsePolicy.parseRetryAfter(connection.getHeaderField("Retry-After")),
        )
        HttpURLConnection.HTTP_BAD_REQUEST -> {
          val error = readBounded(connection.errorStream, MAX_ERROR_BYTES)
          try {
            if (error != null && PeopleApiErrorParser.isExpiredSyncToken(error)) {
              PeopleTransportResult.ExpiredSyncToken
            } else {
              PeopleTransportResult.HttpFailure(status)
            }
          } finally {
            error?.fill(0)
          }
        }
        else -> PeopleTransportResult.HttpFailure(status)
      }
    } catch (_: SocketTimeoutException) {
      PeopleTransportResult.Timeout
    } catch (_: IOException) {
      if (networkAvailability.isOnline()) {
        PeopleTransportResult.NetworkFailure
      } else {
        PeopleTransportResult.Offline
      }
    } catch (_: RuntimeException) {
      PeopleTransportResult.NetworkFailure
    } finally {
      connection.disconnect()
    }
  }

  private fun readSuccess(connection: HttpsURLConnection): PeopleTransportResult {
    if (!PeopleHttpResponsePolicy.isJsonMediaType(connection.contentType)) {
      return PeopleTransportResult.UnexpectedContentType
    }
    val declaredLength = connection.contentLengthLong
    if (declaredLength > maxPageBytes) return PeopleTransportResult.PageTooLarge
    val stream = connection.inputStream ?: return PeopleTransportResult.NetworkFailure
    stream.use { input ->
      val output = ByteArrayOutputStream(
        declaredLength.takeIf { it in 1..maxPageBytes }?.toInt() ?: 8_192,
      )
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var total = 0
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxPageBytes) return PeopleTransportResult.PageTooLarge
        output.write(buffer, 0, read)
      }
      return PeopleTransportResult.Success(output.toByteArray())
    }
  }

  private fun readBounded(stream: InputStream?, maxBytes: Int): ByteArray? {
    if (stream == null) return null
    stream.use { input ->
      val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var total = 0
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
      }
      return output.toByteArray()
    }
  }

  private companion object {
    const val MAX_ERROR_BYTES = 64 * 1024
  }
}

internal object PeopleHttpResponsePolicy {
  fun isJsonMediaType(contentType: String?): Boolean {
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return mediaType == "application/json" || mediaType.endsWith("+json")
  }

  fun parseRetryAfter(value: String?): Long? = value
    ?.trim()
    ?.toLongOrNull()
    ?.takeIf { it in 1..MAX_RETRY_AFTER_SECONDS }

  private const val MAX_RETRY_AFTER_SECONDS = 86_400L
}

internal object PeopleApiErrorParser {
  fun isExpiredSyncToken(bytes: ByteArray): Boolean = runCatching {
    val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8))
    containsExpiredReason(root, depth = 0, remainingNodes = intArrayOf(1_000))
  }.getOrDefault(false)

  private fun containsExpiredReason(
    element: JsonElement,
    depth: Int,
    remainingNodes: IntArray,
  ): Boolean {
    if (depth > 8 || remainingNodes[0]-- <= 0) return false
    if (element.isJsonObject) {
      return element.asJsonObject.entrySet().any { (key, value) ->
        (key == "reason" && value.isJsonPrimitive && value.asString == "EXPIRED_SYNC_TOKEN") ||
          containsExpiredReason(value, depth + 1, remainingNodes)
      }
    }
    if (element.isJsonArray) {
      return element.asJsonArray.any { containsExpiredReason(it, depth + 1, remainingNodes) }
    }
    return false
  }
}
