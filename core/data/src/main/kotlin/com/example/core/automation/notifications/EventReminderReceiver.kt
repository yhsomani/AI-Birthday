package com.example.core.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        val contactId = intent.getStringExtra("contact_id") ?: return

        val db = AppDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val contact = db.contactDao().getById(contactId)
            val event = db.eventDao().getById(eventId)
            if (contact != null && event != null) {
                NotificationHelper.showEventReminderNotification(context, contact, event)
            }
        }
    }
}
