package com.yashsomani.birthdayautopilot.automation.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.yashsomani.birthdayautopilot.storage.database.ArmedAttemptPermit
import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.CallbackTokenEntity
import com.yashsomani.birthdayautopilot.storage.database.CallbackTokenState
import java.util.Locale
import java.util.UUID

internal data class CallbackIdentity(
  val token: CallbackTokenEntity,
  val intent: Intent,
) {
  override fun toString(): String = "CallbackIdentity(<redacted>)"
}

internal object CallbackIdentityFactory {
  private const val ACTION_PREFIX = "com.yashsomani.birthdayautopilot.callback."
  private const val URI_PREFIX = "birthday-autopilot://callback/"

  fun create(
    context: Context,
    permit: ArmedAttemptPermit,
    payload: LocalSendPayload,
    kind: CallbackKind,
    partIndex: Int,
    requestCode: Int,
    createdAtMillis: Long,
    expiresAtMillis: Long,
  ): CallbackIdentity {
    require(requestCode > 0) { "callback-request-code-invalid" }
    require(partIndex in 0 until payload.expectedPartCount) { "callback-part-invalid" }
    require(createdAtMillis >= 0 && expiresAtMillis > createdAtMillis) {
      "callback-lifetime-invalid"
    }
    require(isOpaque(permit.installationId, 64)) { "callback-installation-invalid" }
    require(isOpaque(payload.callbackGeneration, 64)) { "callback-generation-invalid" }
    require(isOpaque(permit.operationId, 80)) { "callback-operation-invalid" }
    val tokenId = UUID.randomUUID().toString().lowercase()
    val kindPath = kind.name.lowercase(Locale.ROOT)
    val action = "$ACTION_PREFIX$kindPath.$tokenId"
    val dataUri = buildString {
      append(URI_PREFIX)
      append(permit.installationId)
      append('/')
      append(payload.callbackGeneration)
      append('/')
      append(permit.purpose.name.lowercase(Locale.ROOT))
      append('/')
      append(permit.operationId)
      append('/')
      append(permit.attemptNumber)
      append('/')
      append(partIndex)
      append('/')
      append(kindPath)
      append('/')
      append(tokenId)
    }
    require(action.length <= 200 && dataUri.length <= 512) { "callback-route-too-long" }
    val receiverClass = when (kind) {
      CallbackKind.SENT -> SmsSentCallbackReceiver::class.java
      CallbackKind.DELIVERY -> SmsDeliveryCallbackReceiver::class.java
    }
    val intent = Intent(action)
      .setData(dataUri.toUri())
      .setPackage(context.packageName)
      .setClass(context, receiverClass)
    val token = CallbackTokenEntity(
      callbackTokenId = tokenId,
      sendAttemptId = permit.sendAttemptId,
      installationId = permit.installationId,
      callbackGeneration = payload.callbackGeneration,
      attemptNumber = permit.attemptNumber,
      partIndex = partIndex,
      kind = kind,
      callbackRequestCode = requestCode,
      action = action,
      dataUri = dataUri,
      mutableForPlatformFillIn = kind == CallbackKind.DELIVERY,
      state = CallbackTokenState.EXPECTED,
      createdAtMillis = createdAtMillis,
      observedAtMillis = null,
      retiredAtMillis = null,
      expiresAtMillis = expiresAtMillis,
    )
    return CallbackIdentity(token, intent)
  }

  fun pendingIntentFlags(kind: CallbackKind, noCreate: Boolean = false): Int {
    var flags = if (noCreate) PendingIntent.FLAG_NO_CREATE else 0
    flags = when (kind) {
      CallbackKind.SENT -> flags or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
      CallbackKind.DELIVERY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        flags or PendingIntent.FLAG_MUTABLE
      } else {
        flags
      }
    }
    return flags
  }

  private fun isOpaque(value: String, maximumLength: Int): Boolean =
    value.length in 1..maximumLength && value.all { character ->
      character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' ||
        character == '.' || character == '_' || character == '-'
    }
}
