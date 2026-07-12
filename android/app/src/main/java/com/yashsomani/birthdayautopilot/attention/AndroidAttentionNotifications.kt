package com.yashsomani.birthdayautopilot.attention

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.yashsomani.birthdayautopilot.MainActivity
import com.yashsomani.birthdayautopilot.R
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

internal object AndroidNativeRouteEvents {
  private val listeners = CopyOnWriteArraySet<() -> Unit>()

  fun subscribe(listener: () -> Unit): () -> Unit {
    listeners.add(listener)
    return { listeners.remove(listener) }
  }

  fun publish() {
    listeners.forEach { listener -> runCatching { listener() } }
  }
}

internal class AndroidAttentionRouteStore(context: Context) {
  private val baseFile =
    File(context.applicationContext.noBackupFilesDir, "birthday-attention-routes-v1")
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun issueTapIdentity(): String? = synchronized(FILE_LOCK) {
    val state = read() ?: return null
    val token = randomUuid()
    val pending = (state.pending + token).takeLast(MAX_PENDING_TAPS)
    token.takeIf { write(state.copy(pending = pending)) }
  }

  fun acceptTapIdentity(token: String): Boolean = synchronized(FILE_LOCK) {
    if (!UUID_PATTERN.matches(token)) return false
    val state = read() ?: return false
    if (token !in state.pending) return false
    val next = state.copy(
      acceptedRouteId = randomUuid(),
      pending = state.pending.filterNot { it == token },
    )
    write(next).also { accepted -> if (accepted) AndroidNativeRouteEvents.publish() }
  }

  fun consumeRouteId(): String? = synchronized(FILE_LOCK) {
    val state = read() ?: return null
    val route = state.acceptedRouteId ?: return null
    route.takeIf { write(state.copy(acceptedRouteId = null)) }
  }

  private fun read(): RouteState? {
    if (!atomicExists()) return RouteState(null, emptyList())
    return try {
      val bytes = file.openRead().use { stream ->
        if (!baseFile.isFile || baseFile.length() !in 1L..2_048L) return null
        stream.readBytes()
      }
      val lines = bytes.toString(StandardCharsets.US_ASCII).lines()
      if (lines.size !in 3..(MAX_PENDING_TAPS + 3) || lines[0] != "1" || lines.last().isNotEmpty()) {
        return null
      }
      val accepted = lines[1].ifEmpty { null }
      val pending = lines.subList(2, lines.lastIndex)
      if (
        accepted?.let { !UUID_PATTERN.matches(it) } == true ||
        pending.any { !UUID_PATTERN.matches(it) } ||
        pending.toSet().size != pending.size
      ) return null
      RouteState(accepted, pending)
    } catch (_: Exception) {
      null
    }
  }

  private fun write(state: RouteState): Boolean {
    if (
      state.acceptedRouteId?.let { !UUID_PATTERN.matches(it) } == true ||
      state.pending.size > MAX_PENDING_TAPS ||
      state.pending.any { !UUID_PATTERN.matches(it) } ||
      state.pending.toSet().size != state.pending.size
    ) return false
    val bytes = buildString {
      append("1\n")
      append(state.acceptedRouteId.orEmpty()).append('\n')
      state.pending.forEach { append(it).append('\n') }
    }.toByteArray(StandardCharsets.US_ASCII)
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
      return false
    }
    return try {
      stream.write(bytes)
      stream.fd.sync()
      file.finishWrite(stream)
      true
    } catch (_: Exception) {
      file.failWrite(stream)
      false
    } finally {
      bytes.fill(0)
    }
  }

  private data class RouteState(
    val acceptedRouteId: String?,
    val pending: List<String>,
  )

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  private companion object {
    val FILE_LOCK = Any()
    const val MAX_PENDING_TAPS = 16
    val UUID_PATTERN = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    fun randomUuid(): String = UUID.randomUUID().toString().lowercase()
  }
}

internal enum class AttentionCategory(val notificationId: Int) {
  ACCOUNT(3_101),
  TRANSFER(3_102),
  COORDINATION(3_103),
  CONTACTS(3_104),
  DEVICE_POLICY(3_105),
  SIM(3_106),
  MISSED(3_107),
  FAILURE(3_108),
  UNKNOWN(3_109),
}

internal data class AttentionClassification(
  val category: AttentionCategory,
  val severity: Int,
)

/**
 * Only reviewed, stable native result codes may notify. Exact matching is intentional: server,
 * Play Services, and OEM strings must never gain notification behavior by sharing a substring.
 */
internal object AttentionClassificationPolicy {
  private val classifications = mapOf(
    "ACCOUNT_NOT_CONNECTED" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "ACCOUNT_DELETING" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "SENDER_REGISTRATION_UNAVAILABLE" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "INSTALLATION_BINDING_UNAVAILABLE" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "TEST_BINDING_INVALID" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "ACTIVE_SENDER_OTHER_DEVICE" to AttentionClassification(AttentionCategory.TRANSFER, 2),
    "SENDER_TRANSFER_PENDING" to AttentionClassification(AttentionCategory.TRANSFER, 2),
    "COORDINATION_NETWORK_UNAVAILABLE" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "COORDINATION_TIER_CONFIGURATION_MISSING" to AttentionClassification(AttentionCategory.COORDINATION, 2),
    "BIRTHDAY_LEASE_UNAVAILABLE" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "TEST_LEASE_UNAVAILABLE" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "BIRTHDAY_CLAIM_PENDING" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "TEST_CLAIM_UNAVAILABLE" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "ARM_STATUS_PENDING" to AttentionClassification(AttentionCategory.COORDINATION, 1),
    "CLOCK_TRUST_UNAVAILABLE" to AttentionClassification(AttentionCategory.DEVICE_POLICY, 2),
    "SMS_ENVIRONMENT_BLOCKED" to AttentionClassification(AttentionCategory.DEVICE_POLICY, 2),
    "SMS_DEADLINE_OR_SIM_CHANGED" to AttentionClassification(AttentionCategory.SIM, 2),
    "ARM_COORDINATION_UNKNOWN" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "SUBMISSION_OUTCOME_UNKNOWN" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "SMS_PRIOR_BOUNDARY_UNRESOLVED" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "SMS_FINAL_GATE_CLOSED" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "SMS_PLATFORM_CALL_UNCERTAIN" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "SMS_ACCEPTED_STATE_UNCERTAIN" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "ARMED_SUPPRESSED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "ARM_LOCAL_DISPATCH_REJECTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "ARM_PERMIT_LOST" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "ARM_SPACING_OVERFLOW" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "PERMIT_NOT_ACTIONABLE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "TEST_JOB_UNAVAILABLE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_PAYLOAD_UNAVAILABLE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_ATTEMPT_UNAVAILABLE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_BINDING_INVALID" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_SEGMENT_PLAN_CHANGED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_CALLBACK_WINDOW_INVALID" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_CALLBACK_IDENTITY_FAILED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_CALLBACK_REGISTRATION_FAILED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_CALLBACK_COLLISION" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_BOOT_ANCHOR_UNAVAILABLE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_API_BARRIER_REJECTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_TEST_LEFT_FOREGROUND" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "LIFECYCLE_JOURNAL_UNREADABLE" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "LIFECYCLE_OPERATION_PENDING" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "BIRTHDAY_MISSED" to AttentionClassification(AttentionCategory.MISSED, 2),
    "BIRTHDAY_DELIVERY_FAILED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "BIRTHDAY_PARTIAL_DELIVERY" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "BIRTHDAY_OUTCOME_UNKNOWN" to AttentionClassification(AttentionCategory.UNKNOWN, 2),
    "RECONCILE_ATTEMPTS_EXHAUSTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CONTACTS_AUTHORIZATION_REQUIRED" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "CONTACTS_PERMISSION_DENIED" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "CONTACTS_PROVIDER_MALFORMED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CONTACTS_BOUND_EXCEEDED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CONTACTS_STORAGE_FAILURE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CONTACTS_BACKGROUND_ATTEMPTS_EXHAUSTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "DATA_RETENTION_ATTEMPTS_EXHAUSTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_EVIDENCE_ATTEMPTS_EXHAUSTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "SMS_REPORT_ATTEMPTS_EXHAUSTED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_CANCELLATION_FAILED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_EXPIRY_INVALID" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_EXPIRY_STALE" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_GENERATION_CHANGED" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_GENERATION_INVALID" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "CALLBACK_IDENTITY_INVALID" to AttentionClassification(AttentionCategory.FAILURE, 2),
    "installation-identity-unavailable" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "keystore-key-missing" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-missing" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-create-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "storage-key-clear-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "keystore-read-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "keystore-key-create-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-encrypt-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-size-invalid" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-decrypt-failed" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
    "wrapped-key-file-invalid" to AttentionClassification(AttentionCategory.ACCOUNT, 2),
  )

  fun classify(code: String): AttentionClassification? = classifications[code]
}

internal class AndroidAttentionNotifier(context: Context) {
  private val appContext = context.applicationContext
  private val manager = appContext.getSystemService(NotificationManager::class.java)
  private val routes = AndroidAttentionRouteStore(appContext)
  private val dedupe = AttentionDedupeStore(appContext)

  fun onSafeCode(code: String) {
    synchronized(NOTIFICATION_LOCK) {
      val classified = AttentionClassificationPolicy.classify(code) ?: return@synchronized
      val localDate = Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
      if (
        (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
          appContext,
          Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED) ||
        settingsRequired(appContext) ||
        !dedupe.isEligible(classified.category, localDate, classified.severity)
      ) return@synchronized
      val tapIdentity = routes.issueTapIdentity() ?: return@synchronized
      val posted = runCatching {
        createChannel()
        val intent = Intent(appContext, MainActivity::class.java)
          .setAction(ACTION_OPEN_ATTENTION)
          .putExtra(EXTRA_TAP_IDENTITY, tapIdentity)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
          appContext,
          classified.category.notificationId,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_launcher_monochrome)
          .setContentTitle(appContext.getString(R.string.attention_notification_title))
          .setContentText(appContext.getString(R.string.attention_notification_body))
          .setCategory(Notification.CATEGORY_STATUS)
          .setVisibility(Notification.VISIBILITY_PRIVATE)
          .setOnlyAlertOnce(true)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)
          .build()
        postAfterRuntimePermissionCheck(
          classified.category.notificationId,
          notification,
        )
        true
      }.getOrDefault(false)
      if (posted) dedupe.reserve(classified.category, localDate, classified.severity)
    }
  }

  fun acceptIntent(intent: Intent?): Boolean {
    if (intent?.action != ACTION_OPEN_ATTENTION) return false
    val identity = intent.getStringExtra(EXTRA_TAP_IDENTITY) ?: return false
    intent.removeExtra(EXTRA_TAP_IDENTITY)
    return routes.acceptTapIdentity(identity)
  }

  private fun createChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      appContext.getString(R.string.attention_channel_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = appContext.getString(R.string.attention_channel_description)
      lockscreenVisibility = Notification.VISIBILITY_PRIVATE
      setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
  }

  // onSafeCode returns before reaching this boundary unless the Android 13+
  // runtime permission is granted. Keep the lint suppression at the one
  // framework call instead of hiding checks across the notifier.
  @SuppressLint("NotificationPermission")
  private fun postAfterRuntimePermissionCheck(id: Int, notification: Notification) {
    manager.notify(id, notification)
  }

  companion object {
    private val NOTIFICATION_LOCK = Any()
    const val ACTION_OPEN_ATTENTION =
      "com.yashsomani.birthdayautopilot.action.OPEN_ATTENTION"
    private const val EXTRA_TAP_IDENTITY =
      "com.yashsomani.birthdayautopilot.extra.ATTENTION_TAP_IDENTITY"
    private const val CHANNEL_ID = "birthday-attention-v1"

    fun settingsRequired(context: Context): Boolean = runCatching {
      val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
      !manager.areNotificationsEnabled() ||
        manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(true)
  }
}

private class AttentionDedupeStore(context: Context) {
  private val baseFile =
    File(context.applicationContext.noBackupFilesDir, "birthday-attention-dedupe-v1")
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun isEligible(
    category: AttentionCategory,
    localDate: String,
    severity: Int,
  ): Boolean = synchronized(FILE_LOCK) {
    if (!DATE.matches(localDate) || severity !in 1..2) return false
    val entries = read() ?: return false
    val existing = entries.firstOrNull { it.category == category && it.localDate == localDate }
    existing == null || existing.severity < severity
  }

  fun reserve(
    category: AttentionCategory,
    localDate: String,
    severity: Int,
  ): Boolean = synchronized(FILE_LOCK) {
    if (!DATE.matches(localDate) || severity !in 1..2) return false
    val entries = read() ?: return false
    val existing = entries.firstOrNull { it.category == category && it.localDate == localDate }
    if (existing != null && existing.severity >= severity) return false
    val next = (entries.filterNot {
      it.category == category && it.localDate == localDate
    } + Entry(category, localDate, severity)).takeLast(MAX_ENTRIES)
    write(next)
  }

  private fun read(): List<Entry>? {
    if (!atomicExists()) return emptyList()
    return try {
      val bytes = file.openRead().use { stream ->
        if (!baseFile.isFile || baseFile.length() !in 1L..4_096L) return null
        stream.readBytes()
      }
      val lines = bytes.toString(StandardCharsets.US_ASCII).lines()
      if (lines.firstOrNull() != "1" || lines.lastOrNull()?.isNotEmpty() != false) return null
      lines.subList(1, lines.lastIndex).map { line ->
        val parts = line.split('|')
        if (parts.size != 3) return null
        val category = runCatching { AttentionCategory.valueOf(parts[0]) }.getOrNull() ?: return null
        val date = parts[1].takeIf(DATE::matches) ?: return null
        val severity = parts[2].toIntOrNull()?.takeIf { it in 1..2 } ?: return null
        Entry(category, date, severity)
      }.takeIf { entries ->
        entries.size <= MAX_ENTRIES &&
          entries.distinctBy { it.category to it.localDate }.size == entries.size
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun write(entries: List<Entry>): Boolean {
    val bytes = buildString {
      append("1\n")
      entries.forEach { append(it.category.name).append('|').append(it.localDate).append('|')
        .append(it.severity).append('\n') }
    }.toByteArray(StandardCharsets.US_ASCII)
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
      return false
    }
    return try {
      stream.write(bytes)
      stream.fd.sync()
      file.finishWrite(stream)
      true
    } catch (_: Exception) {
      file.failWrite(stream)
      false
    } finally {
      bytes.fill(0)
    }
  }

  private data class Entry(
    val category: AttentionCategory,
    val localDate: String,
    val severity: Int,
  )

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  private companion object {
    val FILE_LOCK = Any()
    const val MAX_ENTRIES = 64
    val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
  }
}
