package com.example.domain.model.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class PendingMessageRecord(
    val id: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val shortVariant: String,
    val standardVariant: String,
    val longVariant: String,
    val formalVariant: String,
    val funnyVariant: String,
    val emotionalVariant: String,
    val selectedVariant: String = "standard",
    val selectedVariantText: String = "",
    val channel: MessageChannel,
    val scheduledForMs: Long,
    val approvalMode: ApprovalMode,
    val status: MessageStatus = MessageStatus.PENDING,
    val aiModel: String = "flash",
    val generatedAtMs: Long = 0L,
    val editedByUser: Boolean = false,
    val userEditedText: String? = null,
    val qualityScore: Int = 0,
    val tone: String = "WARM",
    val length: String = "STANDARD",
    val includeEmoji: Boolean = true,
    val scheduledYear: Int = 0,
    val isUsingFallback: Boolean = false,
) {
    fun selectedDispatchText(): String {
        return (if (editedByUser) userEditedText else null) ?: selectedVariantText.ifBlank {
            when (selectedVariant) {
                "short" -> shortVariant
                "long" -> longVariant
                "funny" -> funnyVariant
                "formal" -> formalVariant
                "emotional" -> emotionalVariant
                else -> standardVariant
            }
        }
    }
}
