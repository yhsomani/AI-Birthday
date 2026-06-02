package com.example.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.automation.sender.MessageDispatcher
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class MessageDispatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: ContactDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString("event_id") ?: return Result.failure()

        val pendingMsg = pendingMessageDao.getByEventId(eventId) ?: return Result.failure()
        if (pendingMsg.status != "APPROVED") return Result.success()

        val contact = contactDao.getById(pendingMsg.contactId) ?: return Result.failure()

        val dispatcher = MessageDispatcher(context, pendingMessageDao, sentMessageDao)
        dispatcher.dispatch(pendingMsg, contact)

        return Result.success()
    }
}
