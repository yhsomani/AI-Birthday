package com.example.core.automation.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.core.automation.scheduler.MessageDispatchReceiver
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.core.db.dao.PendingMessageDao
import com.example.domain.repository.MessageRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ApprovalReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApprovalReceiverEntryPoint {
        fun pendingMessageDao(): PendingMessageDao
        fun messageRepository(): MessageRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: intent.getStringExtra("action") ?: return
        val eventId = intent.getStringExtra("event_id") ?: ""
        val messageId = intent.getStringExtra("message_id") ?: ""

        val notificationId = if (messageId.isNotEmpty()) messageId.hashCode() else eventId.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ApprovalReceiverEntryPoint::class.java
        )
        val pendingMessageDao = entryPoint.pendingMessageDao()
        val messageRepository = entryPoint.messageRepository()

        CoroutineScope(Dispatchers.IO).launch {
            val pending = if (messageId.isNotEmpty()) {
                messageRepository.getAllPending().first().find { it.id == messageId }
            } else null
            val resolvedEventId = pending?.eventId ?: eventId

            when (action) {
                "ACTION_APPROVE", "APPROVE" -> {
                    if (messageId.isNotEmpty()) {
                        pendingMessageDao.updateStatus(messageId, "DISPATCHING")
                    } else if (resolvedEventId.isNotEmpty()) {
                        pendingMessageDao.updateStatusByEventId(resolvedEventId, "DISPATCHING")
                    }
                    
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueue(MessageDispatchWorkRequests.create(resolvedEventId))
                }
                "ACTION_REJECT", "REJECT", "SKIP" -> {
                    if (messageId.isNotEmpty()) {
                        pendingMessageDao.updateStatus(messageId, "REJECTED")
                    } else if (resolvedEventId.isNotEmpty()) {
                        pendingMessageDao.updateStatusByEventId(resolvedEventId, "REJECTED")
                    }
                    
                    if (resolvedEventId.isNotEmpty()) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java)
                        val dispatchIntent = Intent(context, MessageDispatchReceiver::class.java).apply {
                            putExtra("event_id", resolvedEventId)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            resolvedEventId.hashCode(),
                            dispatchIntent,
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                        )
                        if (pendingIntent != null) {
                            alarmManager.cancel(pendingIntent)
                            pendingIntent.cancel()
                        }
                    }
                }
                "ACTION_APPROVE_REVIVAL" -> {
                    if (messageId.isNotEmpty()) {
                        pendingMessageDao.updateStatus(messageId, "APPROVED")
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(MessageDispatchWorkRequests.create(resolvedEventId))
                    }
                }
                "ACTION_RETRY" -> {
                    if (messageId.isNotEmpty()) {
                        pendingMessageDao.updateStatus(messageId, "PENDING")
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(MessageDispatchWorkRequests.create(resolvedEventId))
                    }
                }
            }
        }
    }
}
