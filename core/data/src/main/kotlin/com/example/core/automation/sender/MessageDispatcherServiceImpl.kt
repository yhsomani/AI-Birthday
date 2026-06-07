package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.service.MessageDispatcherService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDispatcherServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: com.example.core.db.dao.ContactDao
) : MessageDispatcherService {

    override suspend fun dispatch(message: PendingMessageEntity, contact: ContactEntity) {
        val dispatcher = MessageDispatcher(context, pendingMessageDao, sentMessageDao, contactDao)
        dispatcher.dispatch(message, contact)
    }
}
