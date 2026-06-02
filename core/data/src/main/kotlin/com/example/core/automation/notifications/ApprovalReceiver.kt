package com.example.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import com.example.automation.scheduler.DailyScheduler
import com.example.core.db.dao.PendingMessageDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApprovalReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApprovalReceiverEntryPoint {
        fun pendingMessageDao(): PendingMessageDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val eventId = intent.getStringExtra("event_id") ?: return

        NotificationManagerCompat.from(context).cancel(eventId.hashCode())

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ApprovalReceiverEntryPoint::class.java
        )
        val pendingMessageDao = entryPoint.pendingMessageDao()

        CoroutineScope(Dispatchers.IO).launch {
            if (action == "APPROVE") {
                pendingMessageDao.updateStatusByEventId(eventId, "APPROVED")
                DailyScheduler.scheduleExactSend(context, eventId)
            } else if (action == "SKIP") {
                pendingMessageDao.updateStatusByEventId(eventId, "SKIPPED")
            }
        }
    }
}
