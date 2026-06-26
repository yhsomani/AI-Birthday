package com.example.core.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.core.db.dao.PendingMessageDao
import com.example.domain.model.MessageStatus
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

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                            pendingMessageDao.updateStatus(pending.id, MessageStatus.REJECTED.raw)
                            DailyScheduler.cancelExactSend(context, pending.id)
                        } else if (resolvedEventId.isNotEmpty()) {
                            pendingMessageDao.updateStatusByEventId(resolvedEventId, MessageStatus.REJECTED.raw)
                            DailyScheduler.cancelLegacyExactSend(context, resolvedEventId)
                        }
                    }
                    "ACTION_RETRY" -> {
                        retryNow(context, pendingMessageDao, pending?.id, resolvedEventId)
                    }
                }
            } finally {
                pendingResult.finish()
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

        when (ApprovalNotificationActionPolicy.approveAction(pending)) {
            ApprovalNotificationAction.ApproveAndDispatchNow -> {
                pendingMessageDao.updateStatus(pending.id, MessageStatus.APPROVED.raw)
                WorkManager.getInstance(context)
                    .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
            }
            is ApprovalNotificationAction.ApproveAndSchedule -> {
                pendingMessageDao.updateStatus(pending.id, MessageStatus.APPROVED.raw)
                DailyScheduler.scheduleExactSend(context, pending.id)
            }
            is ApprovalNotificationAction.Expire -> {
                pendingMessageDao.updateStatus(pending.id, MessageStatus.EXPIRED.raw)
                DailyScheduler.cancelExactSend(context, pending.id)
            }
            is ApprovalNotificationAction.Blocked -> {
                DailyScheduler.cancelExactSend(context, pending.id)
            }
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

        pendingMessageDao.updateStatus(pending.id, MessageStatus.APPROVED.raw)
        WorkManager.getInstance(context)
            .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
    }
}
