package com.example.core.automation.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.core.db.dao.PendingMessageDao
import com.example.domain.model.MessageStatus
import com.example.domain.model.message.MessageDraft
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
                val pending = pendingMessageDao.getApprovalNotificationDraftByIdOrOccasion(
                    messageId = messageId,
                    eventId = eventId,
                )
                val resolvedEventId = pending?.occasionId?.value ?: eventId

                when (action) {
                    "ACTION_APPROVE", "APPROVE", "ACTION_APPROVE_REVIVAL" ->
                        approveAndScheduleOrDispatch(context, pendingMessageDao, pending)
                    "ACTION_REJECT", "REJECT", "SKIP" -> {
                        if (pending != null) {
                            pendingMessageDao.savePendingMessageStatus(pending, MessageStatus.REJECTED)
                            context.cancelExactSend(pending.toExactSendCommand())
                        } else if (resolvedEventId.isNotEmpty()) {
                            pendingMessageDao.savePendingMessageStatusByOccasion(resolvedEventId, MessageStatus.REJECTED)
                            resolvedEventId.toLegacyExactSendCancelCommand()?.let { command ->
                                context.cancelLegacyExactSend(command)
                            }
                        }
                    }
                    "ACTION_RETRY" -> {
                        retryNow(context, pendingMessageDao, pending)
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
        pending: MessageDraft?,
    ) {
        if (pending == null) return

        when (ApprovalNotificationActionPolicy.approveAction(pending)) {
            ApprovalNotificationAction.ApproveAndDispatchNow -> {
                pendingMessageDao.savePendingMessageStatus(pending, MessageStatus.APPROVED)
                context.enqueueMessageDispatchWork(pending.toMessageDispatchWorkCommand())
            }
            is ApprovalNotificationAction.ApproveAndSchedule -> {
                pendingMessageDao.savePendingMessageStatus(pending, MessageStatus.APPROVED)
                context.scheduleExactSend(pending.toExactSendCommand())
            }
            is ApprovalNotificationAction.Expire -> {
                pendingMessageDao.savePendingMessageStatus(pending, MessageStatus.EXPIRED)
                context.cancelExactSend(pending.toExactSendCommand())
            }
            is ApprovalNotificationAction.Blocked -> {
                context.cancelExactSend(pending.toExactSendCommand())
            }
        }
    }

    private suspend fun retryNow(
        context: Context,
        pendingMessageDao: PendingMessageDao,
        pending: MessageDraft?,
    ) {
        if (pending == null) return

        pendingMessageDao.savePendingMessageStatus(pending, MessageStatus.APPROVED)
        context.enqueueMessageDispatchWork(pending.toMessageDispatchWorkCommand())
    }
}
