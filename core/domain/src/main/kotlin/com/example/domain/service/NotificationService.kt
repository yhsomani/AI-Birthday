package com.example.domain.service

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity

interface NotificationService {
    fun showApprovalNotification(
        contact: ContactEntity,
        event: EventEntity,
        variants: MessageVariantsResult
    )
}
