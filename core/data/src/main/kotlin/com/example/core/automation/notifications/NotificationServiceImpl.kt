package com.example.core.automation.notifications

import android.content.Context
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.gemini.MessageVariants
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationService {

    override fun showApprovalNotification(
        contact: ContactEntity,
        event: EventEntity,
        variants: MessageVariantsResult,
        messageId: String
    ) {
        val mappedVariants = MessageVariants(
            short = variants.short,
            standard = variants.standard,
            long = variants.long,
            formal = variants.formal,
            funny = variants.funny,
            emotional = variants.emotional,
            recommended = variants.recommended
        )
        NotificationHelper.showApprovalNotification(context, contact, event, mappedVariants, messageId)
    }
}
