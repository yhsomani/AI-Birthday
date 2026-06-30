package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.EmailAddressSyntaxPolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnableFullAutomationUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val schedulerService: SchedulerService,
) {
    suspend operator fun invoke(): Outcome {
        val channelBlackoutJson = preferencesRepository.getChannelBlackout()
        val senderEmail = preferencesRepository.getSenderEmail()
        val senderEmailPassword = preferencesRepository.getSenderEmailPassword()
        var updatedContacts = 0
        var promoted = 0
        var skippedNoRoute = 0
        var skippedNeedsReview = 0

        contactRepository.getAllSync()
            .filter { contact ->
                ApprovalMode.fromRaw(contact.automationMode) != ApprovalMode.DEFAULT ||
                    contact.skipAutoWish
            }
            .forEach { contact ->
                contactRepository.update(
                    contact.copy(
                        automationMode = ApprovalMode.DEFAULT.raw,
                        skipAutoWish = false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                updatedContacts++
            }

        messageRepository.getAllPendingSync()
            .filter { MessageStatus.fromRaw(it.status) == MessageStatus.PENDING }
            .forEach { pending ->
                if (!pending.hasAutomaticRoute(channelBlackoutJson, senderEmail, senderEmailPassword)) {
                    skippedNoRoute++
                    return@forEach
                }
                if (!pending.canPromoteToFullyAutomatic()) {
                    skippedNeedsReview++
                    return@forEach
                }

                messageRepository.insertPending(
                    pending.copy(
                        approvalMode = ApprovalMode.FULLY_AUTO.raw,
                        status = MessageStatus.APPROVED.raw,
                    ),
                )
                schedulerService.scheduleExactSend(pending.id)
                promoted++
            }

        preferencesRepository.setGlobalAutomationMode(ApprovalMode.FULLY_AUTO)

        return Outcome(
            updatedContacts = updatedContacts,
            promotedMessages = promoted,
            skippedWithoutRoute = skippedNoRoute,
            skippedNeedsReview = skippedNeedsReview,
        )
    }

    private suspend fun PendingMessageEntity.hasAutomaticRoute(
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Boolean {
        val recipient = contactRepository.getMessageDispatchRecipient(contactId) ?: return false
        val selection = AutoSendChannelSelector.selectRoute(
            contact = ContactDeliveryRouteProfile(
                preferredChannel = MessageChannel.fromRaw(channel),
                hasPrimaryPhone = !recipient.primaryPhone.isNullOrBlank(),
                hasPrimaryEmail = EmailAddressSyntaxPolicy.isUsableAddress(recipient.primaryEmail),
            ),
            routeHistory = emptyList(),
            channelBlackoutJson = channelBlackoutJson,
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )
        return selection.hasAvailableRoute
    }

    private fun PendingMessageEntity.canPromoteToFullyAutomatic(): Boolean {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = selectedDispatchText(),
            isUsingFallback = isUsingFallback,
        )
        return decision.approvalMode == ApprovalMode.FULLY_AUTO
    }

    private fun PendingMessageEntity.selectedDispatchText(): String {
        return (if (editedByUser) userEditedText else null) ?: selectedVariantText.ifBlank {
            when (selectedVariant) {
                "short" -> shortVariant
                "long" -> longVariant
                "funny" -> funnyVariant
                "formal" -> formalVariant
                else -> standardVariant
            }
        }
    }

    data class Outcome(
        val updatedContacts: Int,
        val promotedMessages: Int,
        val skippedWithoutRoute: Int,
        val skippedNeedsReview: Int = 0,
    )
}
