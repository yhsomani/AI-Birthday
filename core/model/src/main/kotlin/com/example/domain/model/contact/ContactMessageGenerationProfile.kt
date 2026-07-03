package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.common.ContactId

data class ContactMessageGenerationProfile(
    val id: ContactId,
    val relationshipType: String,
    val automationMode: ApprovalMode,
    val skipAutoWish: Boolean,
    val deliveryRouteProfile: ContactDeliveryRouteProfile,
    val promptContext: ContactMessagePromptContext,
    val header: ContactHeader,
    val customSendTimeHour: Int?,
    val customSendTimeMinute: Int?,
)
