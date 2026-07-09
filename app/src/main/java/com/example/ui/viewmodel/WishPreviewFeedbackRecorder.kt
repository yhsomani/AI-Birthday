package com.example.ui.viewmodel

import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.message.MessageFeedbackRecord
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.MessageFeedbackRepository
import java.util.UUID

internal class WishPreviewFeedbackRecorder(
    private val messageFeedbackRepository: MessageFeedbackRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun record(
        draft: WishPreviewDraft,
        option: AiFeedbackOption,
        draftText: String,
    ) {
        messageFeedbackRepository.record(
            MessageFeedbackRecord(
                id = MessageFeedbackId(idFactory()),
                pendingMessageId = draft.id,
                contactId = draft.contactId,
                occasionId = draft.occasionId,
                reasonKey = option.key,
                instruction = option.instruction,
                draftText = draftText,
                createdAtMs = currentTimeMs(),
            )
        )
        activityLogRepository.record(
            ActivityLogRecord(
                id = idFactory(),
                type = ActivityLogType.AI.raw,
                title = "AI feedback saved",
                detail = option.instruction,
                contactId = draft.contactId.value,
                eventId = draft.occasionId.value,
                messageId = draft.id.value,
                severity = ActivityLogSeverity.INFO.raw,
                status = ActivityLogStatus.OPEN.raw,
                actionRoute = "wish/${draft.contactId.value}/${draft.id.value}",
                metadataJson = "{\"feedback\":\"${option.key}\"}",
            )
        )
    }
}
