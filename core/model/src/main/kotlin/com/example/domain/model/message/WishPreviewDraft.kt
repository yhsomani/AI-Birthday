package com.example.domain.model.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class WishPreviewDraft(
    val id: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val variants: WishPreviewVariants,
    val selectedVariant: String,
    val selectedVariantText: String,
    val channel: MessageChannel,
    val scheduledForMs: Long,
    val approvalMode: ApprovalMode,
    val status: MessageStatus,
    val isUsingFallback: Boolean,
) {
    fun variantText(variant: String): String = variants.textFor(variant)
}

data class WishPreviewVariants(
    val short: String,
    val standard: String,
    val long: String,
    val formal: String,
    val funny: String,
    val emotional: String,
) {
    fun textFor(variant: String): String {
        return when (variant.trim().lowercase()) {
            "short" -> short
            "standard" -> standard
            "long" -> long
            "formal" -> formal
            "funny" -> funny
            "emotional" -> emotional
            else -> standard
        }
    }
}

data class WishPreviewReviewItem(
    val id: MessageDraftId,
    val contactId: ContactId,
    val scheduledForMs: Long,
    val status: MessageStatus,
)
