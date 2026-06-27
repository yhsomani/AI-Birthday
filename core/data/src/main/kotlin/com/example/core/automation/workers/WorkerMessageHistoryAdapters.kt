package com.example.core.automation.workers

import com.example.core.db.dao.SentMessageDao
import com.example.domain.message.toDeliveryRouteHistoryRecords
import com.example.domain.model.message.DeliveryRouteHistoryRecord

internal suspend fun SentMessageDao.getDeliveryRouteHistoryByContact(
    contactId: String,
): List<DeliveryRouteHistoryRecord> {
    return getByContact(contactId).toDeliveryRouteHistoryRecords()
}
