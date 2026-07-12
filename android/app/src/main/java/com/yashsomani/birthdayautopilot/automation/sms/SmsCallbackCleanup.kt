package com.yashsomani.birthdayautopilot.automation.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.CallbackTokenEntity

internal sealed interface SmsCallbackCleanupResult {
  data class Completed(val tokenCount: Int) : SmsCallbackCleanupResult
  data class Refused(val safeCode: String) : SmsCallbackCleanupResult
}

/** Cancels platform PendingIntents before making their durable callback identities unusable. */
internal class SmsCallbackCleanup(
  context: Context,
  private val database: BirthdayDatabase,
) {
  private val appContext = context.applicationContext
  private val dao get() = database.smsOutcomeDao()

  suspend fun cancelAndRetireGeneration(
    installationId: String,
    callbackGeneration: String,
    retiredAtMillis: Long,
  ): SmsCallbackCleanupResult {
    if (
      !OPAQUE_ID.matches(installationId) ||
      !OPAQUE_ID.matches(callbackGeneration) ||
      retiredAtMillis < 0
    ) return SmsCallbackCleanupResult.Refused("CALLBACK_GENERATION_INVALID")
    val tokens = database.withTransaction {
      dao.liveTokensForGeneration(installationId, callbackGeneration)
    }
    val cancelled = cancel(tokens)
    if (cancelled != null) return SmsCallbackCleanupResult.Refused(cancelled)
    return database.withTransaction {
      val current = dao.liveTokensForGeneration(installationId, callbackGeneration)
      if (!tokens.map { it.callbackTokenId }.toSet().containsAll(current.map { it.callbackTokenId })) {
        return@withTransaction SmsCallbackCleanupResult.Refused("CALLBACK_GENERATION_CHANGED")
      }
      val count = dao.retireGeneration(installationId, callbackGeneration, retiredAtMillis)
      SmsCallbackCleanupResult.Completed(count)
    }
  }

  suspend fun cancelAndExpireDue(
    expiredAtMillis: Long,
    limit: Int = MAX_EXPIRY_BATCH,
  ): SmsCallbackCleanupResult {
    if (expiredAtMillis < 0 || limit !in 1..MAX_EXPIRY_BATCH) {
      return SmsCallbackCleanupResult.Refused("CALLBACK_EXPIRY_INVALID")
    }
    val tokens = database.withTransaction { dao.dueLiveTokens(expiredAtMillis, limit) }
    val cancelled = cancel(tokens)
    if (cancelled != null) return SmsCallbackCleanupResult.Refused(cancelled)
    val ids = tokens.map { it.callbackTokenId }
    if (ids.isEmpty()) return SmsCallbackCleanupResult.Completed(0)
    val count = database.withTransaction { dao.expireTokens(ids, expiredAtMillis) }
    return if (count == ids.size) {
      SmsCallbackCleanupResult.Completed(count)
    } else {
      SmsCallbackCleanupResult.Refused("CALLBACK_EXPIRY_STALE")
    }
  }

  private fun cancel(tokens: List<CallbackTokenEntity>): String? {
    for (token in tokens) {
      val intent = intent(token) ?: return "CALLBACK_IDENTITY_INVALID"
      try {
        PendingIntent.getBroadcast(
          appContext,
          token.callbackRequestCode,
          intent,
          CallbackIdentityFactory.pendingIntentFlags(token.kind, noCreate = true),
        )?.cancel()
      } catch (_: RuntimeException) {
        return "CALLBACK_CANCELLATION_FAILED"
      } catch (_: LinkageError) {
        return "CALLBACK_CANCELLATION_FAILED"
      }
    }
    return null
  }

  private fun intent(token: CallbackTokenEntity): Intent? {
    if (
      token.callbackRequestCode <= 0 ||
      token.action.length !in 1..200 ||
      !token.action.startsWith(ACTION_PREFIX) ||
      token.dataUri.length !in 1..512 ||
      !token.dataUri.startsWith(DATA_URI_PREFIX) ||
      (token.kind == CallbackKind.DELIVERY) != token.mutableForPlatformFillIn
    ) return null
    val receiver = when (token.kind) {
      CallbackKind.SENT -> SmsSentCallbackReceiver::class.java
      CallbackKind.DELIVERY -> SmsDeliveryCallbackReceiver::class.java
    }
    return try {
      Intent(token.action)
        .setData(token.dataUri.toUri())
        .setPackage(appContext.packageName)
        .setClass(appContext, receiver)
    } catch (_: RuntimeException) {
      null
    }
  }

  private companion object {
    const val MAX_EXPIRY_BATCH = 256
    const val ACTION_PREFIX = "com.yashsomani.birthdayautopilot.callback."
    const val DATA_URI_PREFIX = "birthday-autopilot://callback/"
    val OPAQUE_ID = Regex("^[A-Za-z0-9._-]{1,64}$")
  }
}
