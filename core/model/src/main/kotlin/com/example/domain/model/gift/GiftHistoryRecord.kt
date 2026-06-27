package com.example.domain.model.gift

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId

data class GiftHistoryRecord(
    val id: GiftHistoryId,
    val contactId: ContactId,
    val giftName: String,
    val giftCategory: String,
    val occasionType: String,
    val year: Int,
    val approxCostInr: Int,
    val receivedWell: Boolean?,
    val notes: String,
)
