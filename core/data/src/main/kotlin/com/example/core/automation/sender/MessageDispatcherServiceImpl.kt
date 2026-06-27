package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.domain.model.dispatch.MessageDispatchRequest
import com.example.domain.service.MessageDispatcherService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDispatcherServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: com.example.core.db.dao.ContactDao,
    private val eventDao: EventDao,
    private val dispatchAttemptDao: DispatchAttemptDao,
) : MessageDispatcherService {

    override suspend fun dispatch(request: MessageDispatchRequest) {
        val dispatcher = MessageDispatcher(
            context = context,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            eventDao = eventDao,
            dispatchAttemptDao = dispatchAttemptDao,
        )
        dispatcher.dispatch(request)
    }
}
