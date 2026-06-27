package com.example.domain.gift

import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.gift.GiftHistoryRecord

fun GiftHistoryEntity.toRecord(): GiftHistoryRecord {
    return GiftHistoryRecord(
        id = GiftHistoryId(id),
        contactId = ContactId(contactId),
        giftName = giftName,
        giftCategory = giftCategory,
        occasionType = occasionType,
        year = year,
        approxCostInr = approxCostInr,
        receivedWell = receivedWell,
        notes = notes,
    )
}

fun GiftHistoryRecord.toEntity(): GiftHistoryEntity {
    return GiftHistoryEntity(
        id = id.value,
        contactId = contactId.value,
        giftName = giftName,
        giftCategory = giftCategory,
        occasionType = occasionType,
        year = year,
        approxCostInr = approxCostInr,
        receivedWell = receivedWell,
        notes = notes,
    )
}
