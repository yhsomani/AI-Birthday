package com.yashsomani.birthdayautopilot.automation.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yashsomani.birthdayautopilot.automation.sms.SubscriptionChangeFingerprint
import com.yashsomani.birthdayautopilot.automation.sms.SubscriptionChangeSignalStore

/** Receives only protected system lifecycle/clock broadcasts and schedules bounded local work. */
class AutomationReconcileReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val appContext = context.applicationContext
    if (intent.action == ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED) {
      SubscriptionChangeSignalStore(appContext).recordConfirmedChange(
        SubscriptionChangeFingerprint.read(appContext),
      )
      AutomationScheduler.enqueueSubscriptionChange(appContext)
      return
    }
    val trigger = when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_DATE_CHANGED,
      -> "BOOT_OR_CLOCK"
      Intent.ACTION_MY_PACKAGE_REPLACED -> "APP_REPLACED"
      Intent.ACTION_LOCALE_CHANGED -> "FOREGROUND"
      else -> return
    }
    AutomationScheduler.enqueueImmediateLocal(appContext, trigger)
  }

  private companion object {
    const val ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED =
      "android.telephony.action.DEFAULT_SMS_SUBSCRIPTION_CHANGED"
  }
}
