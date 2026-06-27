package com.example.domain.model.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId

data class MessageApprovalState(
    val id: MessageDraftId,
    val selectedVariantText: String,
    val approvalMode: ApprovalMode,
    val status: MessageStatus,
    val editedByUser: Boolean,
    val userEditedText: String?,
) {
    fun approved(finalEditedText: String?): MessageApprovalState {
        return if (finalEditedText != null && finalEditedText != selectedVariantText) {
            copy(
                status = MessageStatus.APPROVED,
                selectedVariantText = finalEditedText,
                editedByUser = true,
                userEditedText = finalEditedText,
            )
        } else {
            copy(status = MessageStatus.APPROVED)
        }
    }

    fun withStatus(status: MessageStatus): MessageApprovalState {
        return copy(status = status)
    }
}
