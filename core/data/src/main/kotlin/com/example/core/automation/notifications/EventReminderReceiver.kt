package com.example.core.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.automation.scheduler.EventReminderScheduler
import com.example.core.db.AppDatabase
import com.example.core.prefs.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SecurePrefs(context).isBirthdayRemindersEnabled()) return
        val eventId = intent.getStringExtra(EventReminderScheduler.EXTRA_EVENT_ID) ?: return
        val contactId = intent.getStringExtra(EventReminderScheduler.EXTRA_CONTACT_ID) ?: return

        val db = AppDatabase.getInstance(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contact = db.contactDao().getById(contactId)
                val event = db.eventDao().getById(eventId)
                if (contact != null && event != null && event.isActive) {
                    NotificationHelper.showEventReminderNotification(context, contact, event)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
