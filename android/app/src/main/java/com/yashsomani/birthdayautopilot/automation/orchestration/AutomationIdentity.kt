package com.yashsomani.birthdayautopilot.automation.orchestration

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

internal data class LocalInstallationIdentity(
  val installationId: String,
  val callbackGeneration: String,
) {
  override fun toString(): String = "LocalInstallationIdentity(<redacted>)"
}

/** Backup-excluded durable identity; malformed/partial files fail closed and are never repaired. */
internal class NoBackupInstallationIdentityStore(context: Context) {
  private val baseFile = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun currentOrNull(): LocalInstallationIdentity? = synchronized(FILE_LOCK) { read() }

  fun getOrCreate(): LocalInstallationIdentity? = synchronized(FILE_LOCK) {
    read()?.let { return it }
    if (atomicExists()) return null
    val created = LocalInstallationIdentity(randomHex(16), randomHex(16))
    val bytes = "${created.installationId}\n${created.callbackGeneration}\n"
      .toByteArray(StandardCharsets.US_ASCII)
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
      return null
    }
    return try {
      stream.write(bytes)
      stream.fd.sync()
      file.finishWrite(stream)
      read()?.takeIf { it == created }
    } catch (_: Exception) {
      file.failWrite(stream)
      null
    } finally {
      bytes.fill(0)
    }
  }

  /** Rotates both local identities only after callbacks and protected work have been drained. */
  fun rotateAfterTeardown(
    expectedInstallationId: String,
    expectedCallbackGeneration: String,
  ): LocalInstallationIdentity? = synchronized(FILE_LOCK) {
    val current = read() ?: return null
    if (
      current.installationId != expectedInstallationId ||
      current.callbackGeneration != expectedCallbackGeneration
    ) return null
    val replacement = LocalInstallationIdentity(randomHex(16), randomHex(16))
    val bytes = "${replacement.installationId}\n${replacement.callbackGeneration}\n"
      .toByteArray(StandardCharsets.US_ASCII)
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
      return null
    }
    return try {
      stream.write(bytes)
      stream.fd.sync()
      file.finishWrite(stream)
      read()?.takeIf { it == replacement }
    } catch (_: Exception) {
      file.failWrite(stream)
      null
    } finally {
      bytes.fill(0)
    }
  }

  private fun read(): LocalInstallationIdentity? {
    if (!atomicExists()) return null
    return try {
      val bytes = file.openRead().use { stream ->
        if (!baseFile.isFile || baseFile.length() !in 66L..128L) return null
        stream.readBytes()
      }
      val lines = bytes.toString(StandardCharsets.US_ASCII).lines()
      bytes.fill(0)
      if (lines.size != 3 || lines.last().isNotEmpty()) return null
      val installation = lines[0]
      val callback = lines[1]
      if (!HEX_128.matches(installation) || !HEX_128.matches(callback)) return null
      LocalInstallationIdentity(installation, callback)
    } catch (_: Exception) {
      null
    }
  }

  private fun randomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return bytes.toHex().also { bytes.fill(0) }
  }

  private fun ByteArray.toHex(): String = joinToString("") { byte ->
    String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
  }

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  private companion object {
    val FILE_LOCK = Any()
    const val FILE_NAME = "birthday-installation-identity-v1"
    val HEX_128 = Regex("^[a-f0-9]{32}$")
  }
}

internal object AutomationOpaqueIds {
  fun sha256(domain: String, vararg values: String): String = digest(domain, *values).toHex()

  fun prefixed(prefix: String, domain: String, vararg values: String): String =
    "${prefix}_${sha256(domain, *values)}"

  /** Deterministic RFC-4122 UUIDv5-shaped request identity for idempotent network replay. */
  fun uuid(domain: String, vararg values: String): String {
    val bytes = digest(domain, *values).copyOfRange(0, 16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.toHex()
    bytes.fill(0)
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
      "${hex.substring(16, 20)}-${hex.substring(20)}"
  }

  private fun digest(domain: String, vararg values: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    (listOf(domain) + values).forEach { value ->
      val bytes = value.toByteArray(StandardCharsets.UTF_8)
      digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
      digest.update(bytes)
      bytes.fill(0)
    }
    return digest.digest()
  }

  private fun ByteArray.toHex(): String = joinToString("") { byte ->
    String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
  }
}
