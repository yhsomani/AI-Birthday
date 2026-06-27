package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.MessageDispatchDraft

data class MessageDispatchState(
    val draft: MessageDraft,
    val dispatchDraft: MessageDispatchDraft,
) {
    val id: MessageDraftId
        get() = draft.id

    val contactId: ContactId
        get() = draft.contactId

    val occasionId: OccasionId
        get() = draft.occasionId

    val status: MessageStatus
        get() = draft.status

    val channel: MessageChannel
        get() = draft.channel

    fun withStatus(status: MessageStatus): MessageDispatchState {
        return copy(draft = draft.copy(status = status))
    }
}
