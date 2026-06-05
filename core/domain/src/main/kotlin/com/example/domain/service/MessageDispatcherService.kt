package com.example.domain.service

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity

interface MessageDispatcherService {
    suspend fun dispatch(message: PendingMessageEntity, contact: ContactEntity)
}
