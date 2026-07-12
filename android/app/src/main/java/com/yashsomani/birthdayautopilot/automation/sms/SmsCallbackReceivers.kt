package com.yashsomani.birthdayautopilot.automation.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.automation.workers.AutomationScheduler
import com.yashsomani.birthdayautopilot.automation.workers.SmsOutcomeWorkScheduler
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEventEntity
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEvidenceClass
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleJournalStatus
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class SmsSentCallbackReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    SmsCallbackDispatcher.enqueue(
      context = context.applicationContext,
      intent = intent,
      resultCode = resultCode,
      kind = CallbackKind.SENT,
      pendingResult = goAsync(),
    )
  }
}

class SmsDeliveryCallbackReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    SmsCallbackDispatcher.enqueue(
      context = context.applicationContext,
      intent = intent,
      resultCode = resultCode,
      kind = CallbackKind.DELIVERY,
      pendingResult = goAsync(),
    )
  }
}

private data class CallbackObservation(
  val action: String,
  val dataUri: String,
  val resultCode: Int,
  val kind: CallbackKind,
  val pdu: ByteArray?,
  val format: String?,
)

private object SmsCallbackDispatcher {
  private const val MAX_PDU_BYTES = 4 * 1024
  private const val MAX_ROUTE_CHARS = 512
  private val executor = ThreadPoolExecutor(
    1,
    2,
    30,
    TimeUnit.SECONDS,
    ArrayBlockingQueue(64),
    { runnable -> Thread(runnable, "BirthdaySmsCallback").apply { isDaemon = true } },
  )

  fun enqueue(
    context: Context,
    intent: Intent,
    resultCode: Int,
    kind: CallbackKind,
    pendingResult: BroadcastReceiver.PendingResult,
  ) {
    val observation = try {
      capture(intent, resultCode, kind)
    } catch (_: RuntimeException) {
      null
    } catch (_: LinkageError) {
      null
    }
    if (observation == null) {
      pendingResult.finish()
      return
    }
    try {
      executor.execute {
        try {
          persist(context, observation)
        } catch (_: Exception) {
          // Callback failure is represented by missing evidence and the watchdog, never logs.
        } catch (_: LinkageError) {
          // Fail closed on platform/ABI linkage errors without exposing private callback context.
        } finally {
          observation.pdu?.fill(0)
          pendingResult.finish()
        }
      }
    } catch (_: RejectedExecutionException) {
      observation.pdu?.fill(0)
      pendingResult.finish()
    }
  }

  private fun capture(
    intent: Intent,
    resultCode: Int,
    kind: CallbackKind,
  ): CallbackObservation? {
    val action = intent.action?.takeIf { it.length in 1..200 } ?: return null
    val dataUri = intent.dataString?.takeIf { it.length in 1..MAX_ROUTE_CHARS } ?: return null
    val pdu = if (kind == CallbackKind.DELIVERY) {
      val rawPdu = intent.getByteArrayExtra("pdu")
      val boundedCopy = rawPdu?.takeIf { it.size in 1..MAX_PDU_BYTES }?.clone()
      rawPdu?.fill(0)
      boundedCopy
    } else {
      null
    }
    val format = if (kind == CallbackKind.DELIVERY) {
      intent.getStringExtra("format")?.takeIf { it == "3gpp" || it == "3gpp2" }
    } else {
      null
    }
    return CallbackObservation(action, dataUri, resultCode, kind, pdu, format)
  }

  private fun persist(context: Context, observation: CallbackObservation) = runBlocking {
    if (
      LifecycleStateStore(context).journalStatus() == LifecycleJournalStatus.UNREADABLE
    ) return@runBlocking
    val database = AppGraph.get(context).database
    val ledger = database.safetyLedgerDao()
    val observedAtMillis = System.currentTimeMillis()
    val token = ledger.findLiveCallbackToken(
      action = observation.action,
      dataUri = observation.dataUri,
      kind = observation.kind,
      observedAtMillis = observedAtMillis,
    ) ?: return@runBlocking
    val evidence = when (observation.kind) {
      CallbackKind.SENT -> SmsCallbackEvidenceClassifier.sent(observation.resultCode)
      CallbackKind.DELIVERY -> SmsCallbackEvidenceClassifier.delivery(
        observation.pdu,
        observation.format,
      )
    }
    val event = DeliveryEventEntity(
      eventId = UUID.randomUUID().toString().lowercase(),
      callbackTokenId = token.callbackTokenId,
      evidenceKey = evidenceKey(
        token.callbackTokenId,
        evidence.first,
        observation.resultCode,
        evidence.second,
      ),
      evidenceClass = evidence.first,
      androidResultCode = observation.resultCode,
      modemStatus = evidence.second,
      receivedAtMillis = observedAtMillis,
    )
    val result = SmsOutcomeProcessor(database).recordCallbackAndReduce(
      sendAttemptId = token.sendAttemptId,
      event = event,
      observedAtMillis = observedAtMillis,
    )
    result.attentionSafeCode?.let { AndroidAttentionNotifier(context).onSafeCode(it) }
    SmsOutcomeWorkScheduler.scheduleFrom(context, result)
    if (result.callbackInserted) {
      AutomationScheduler.enqueueImmediateLocal(context, "CALLBACK")
    }
  }

  private fun evidenceKey(
    tokenId: String,
    evidence: DeliveryEvidenceClass,
    resultCode: Int,
    modemStatus: Int?,
  ): String {
    val value = "$tokenId|${evidence.name}|$resultCode|${modemStatus ?: "none"}"
    return MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(StandardCharsets.US_ASCII))
      .joinToString("") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
      }
  }
}

internal object SmsCallbackEvidenceClassifier {
  fun sent(resultCode: Int): Pair<DeliveryEvidenceClass, Int?> = when (resultCode) {
    Activity.RESULT_OK -> DeliveryEvidenceClass.SENT_SUCCESS to null
    SmsManager.RESULT_ERROR_RADIO_OFF ->
      DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF to null
    SmsManager.RESULT_ERROR_NO_SERVICE ->
      DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE to null
    else -> DeliveryEvidenceClass.SENT_FAILURE to null
  }

  fun delivery(
    pdu: ByteArray?,
    format: String?,
  ): Pair<DeliveryEvidenceClass, Int?> {
    if (pdu == null || format == null) return DeliveryEvidenceClass.DELIVERY_UNKNOWN to null
    val message = try {
      SmsMessage.createFromPdu(pdu, format)
    } catch (_: RuntimeException) {
      null
    } ?: return DeliveryEvidenceClass.DELIVERY_UNKNOWN to null
    if (!message.isStatusReportMessage) return DeliveryEvidenceClass.DELIVERY_UNKNOWN to null
    val status = message.status
    return classifyStatus(status) to status
  }

  fun classifyStatus(status: Int): DeliveryEvidenceClass = when (status) {
      in 0x00..0x1f -> DeliveryEvidenceClass.DELIVERY_COMPLETE
      in 0x20..0x3f -> DeliveryEvidenceClass.DELIVERY_PENDING
      in 0x40..0x7f -> DeliveryEvidenceClass.DELIVERY_FAILED
      else -> DeliveryEvidenceClass.DELIVERY_UNKNOWN
  }
}
