package com.yashsomani.birthdayautopilot.auth

import android.content.Context

enum class TelephonyPermanentDenial {
  NONE,
  PHONE_STATE,
  SMS,
  BOTH,
  UNKNOWN,
  ;

  fun blocksPhoneState(): Boolean = this in setOf(PHONE_STATE, BOTH, UNKNOWN)

  fun blocksSms(): Boolean = this in setOf(SMS, BOTH, UNKNOWN)

  fun readinessWireCode(): String? = when (this) {
    NONE -> null
    PHONE_STATE -> "phone-state-permission-permanently-denied"
    SMS -> "sms-permission-permanently-denied"
    BOTH, UNKNOWN -> "permission-permanently-denied"
  }
}

internal object TelephonyPermissionRemediationPolicy {
  private val PERMANENT_CODES = setOf(
    "permission-permanently-denied",
    "phone-state-permission-permanently-denied",
    "sms-permission-permanently-denied",
  )

  /** Exact issue copy is retained while the existing generic handle opens App Details Settings. */
  fun actionCode(issueCode: String): String =
    if (issueCode in PERMANENT_CODES) "permission-permanently-denied" else issueCode
}

/**
 * Content-free durable memory for Android's "Don't ask again" result. Backup is disabled for the
 * application; synchronous commits make the state visible after immediate process death.
 */
internal class TelephonyPermissionDenialStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES,
    Context.MODE_PRIVATE,
  )

  fun markPhoneStatePermanent(): Boolean = mark(PHONE_STATE_KEY)

  fun markSmsPermanent(): Boolean = mark(SMS_KEY)

  /** Authoritative OS grants clear stale denial markers after a Settings return. */
  fun reconcile(
    phoneStateGranted: Boolean?,
    smsGranted: Boolean?,
  ): TelephonyPermanentDenial = synchronized(FILE_LOCK) {
    try {
      val clearPhone = phoneStateGranted == true && preferences.contains(PHONE_STATE_KEY)
      val clearSms = smsGranted == true && preferences.contains(SMS_KEY)
      if (clearPhone || clearSms) {
        val editor = preferences.edit()
        if (clearPhone) editor.remove(PHONE_STATE_KEY)
        if (clearSms) editor.remove(SMS_KEY)
        if (!editor.commit()) {
          return@synchronized TelephonyPermanentDenial.UNKNOWN
        }
      }
      val phone = phoneStateGranted != true && preferences.getBoolean(PHONE_STATE_KEY, false)
      val sms = smsGranted != true && preferences.getBoolean(SMS_KEY, false)
      when {
        phone && sms -> TelephonyPermanentDenial.BOTH
        phone -> TelephonyPermanentDenial.PHONE_STATE
        sms -> TelephonyPermanentDenial.SMS
        else -> TelephonyPermanentDenial.NONE
      }
    } catch (_: RuntimeException) {
      TelephonyPermanentDenial.UNKNOWN
    } catch (_: LinkageError) {
      TelephonyPermanentDenial.UNKNOWN
    }
  }

  internal fun clearForTests(): Boolean = synchronized(FILE_LOCK) {
    try {
      preferences.edit().clear().commit()
    } catch (_: RuntimeException) {
      false
    } catch (_: LinkageError) {
      false
    }
  }

  private fun mark(key: String): Boolean = synchronized(FILE_LOCK) {
    try {
      preferences.edit().putBoolean(key, true).commit()
    } catch (_: RuntimeException) {
      false
    } catch (_: LinkageError) {
      false
    }
  }

  private companion object {
    const val PREFERENCES = "telephony-permission-denial-v1"
    const val PHONE_STATE_KEY = "phone-state-permanent"
    const val SMS_KEY = "sms-permanent"
    val FILE_LOCK = Any()
  }
}
