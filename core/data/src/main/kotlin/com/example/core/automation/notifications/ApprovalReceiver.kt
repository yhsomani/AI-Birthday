package com.example.core.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.automation.workers.MessageDispatchWorkRequests
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

        CoroutineScope(Dispatchers.IO).launch {
            val pending = if (messageId.isNotEmpty()) {
                pendingMessageDao.getById(messageId)
            } else if (eventId.isNotEmpty()) {
                pendingMessageDao.getByEventId(eventId)
            } else {
                null
            }
            val resolvedEventId = pending?.eventId ?: eventId

            when (action) {
                "ACTION_APPROVE", "APPROVE", "ACTION_APPROVE_REVIVAL" ->
                    approveAndScheduleOrDispatch(context, pendingMessageDao, pending?.id, resolvedEventId)
                "ACTION_REJECT", "REJECT", "SKIP" -> {
                    if (pending != null) {
                        pendingMessageDao.updateStatus(pending.id, "REJECTED")
                        DailyScheduler.cancelExactSend(context, pending.id)
                    } else if (resolvedEventId.isNotEmpty()) {
                        pendingMessageDao.updateStatusByEventId(resolvedEventId, "REJECTED")
                        DailyScheduler.cancelLegacyExactSend(context, resolvedEventId)
                    }
                }
                "ACTION_RETRY" -> {
                    retryNow(context, pendingMessageDao, pending?.id, resolvedEventId)
                }
            }
        }
    }

    private suspend fun approveAndScheduleOrDispatch(
        context: Context,
        pendingMessageDao: PendingMessageDao,
        pendingMessageId: String?,
        eventId: String,
    ) {
        val pending = pendingMessageId?.let { pendingMessageDao.getById(it) }
            ?: eventId.takeIf { it.isNotBlank() }?.let { pendingMessageDao.getByEventId(it) }
            ?: return

        pendingMessageDao.updateStatus(pending.id, "APPROVED")
        if (pending.scheduledForMs <= System.currentTimeMillis()) {
            WorkManager.getInstance(context)
                .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
        } else {
            DailyScheduler.scheduleExactSend(context, pending.id)
        }
    }

    private suspend fun retryNow(
        context: Context,
        pendingMessageDao: PendingMessageDao,
        pendingMessageId: String?,
        eventId: String,
    ) {
        val pending = pendingMessageId?.let { pendingMessageDao.getById(it) }
            ?: eventId.takeIf { it.isNotBlank() }?.let { pendingMessageDao.getByEventId(it) }
            ?: return

        pendingMessageDao.updateStatus(pending.id, "APPROVED")
        WorkManager.getInstance(context)
            .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
    }
}
