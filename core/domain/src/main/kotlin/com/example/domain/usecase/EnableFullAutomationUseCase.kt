package com.example.domain.usecase

import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.EmailAddressSyntaxPolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.message.PendingMessageRecord
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

        contactRepository.getAutomationReadinessProfiles()
            .filter { contact ->
                contact.automationMode != ApprovalMode.DEFAULT ||
                    contact.skipAutoWish
            }
            .forEach { contact ->
                contactRepository.updateAutomationOverride(
                    id = contact.id,
                    automationMode = ApprovalMode.DEFAULT,
                    skipAutoWish = false,
                    updatedAt = System.currentTimeMillis(),
                )
                updatedContacts++
            }

        messageRepository.getAllPendingSync()
            .filter { it.status == MessageStatus.PENDING }
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
                        approvalMode = ApprovalMode.FULLY_AUTO,
                        status = MessageStatus.APPROVED,
                    ),
                )
                schedulerService.scheduleExactSend(pending.id.value)
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

    private suspend fun PendingMessageRecord.hasAutomaticRoute(
        channelBlackoutJson: String,
        senderEmail: String,
        senderEmailPassword: String,
    ): Boolean {
        val recipient = contactRepository.getMessageDispatchRecipient(contactId.value) ?: return false
        val selection = AutoSendChannelSelector.selectRoute(
            contact = ContactDeliveryRouteProfile(
                preferredChannel = channel,
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

    private fun PendingMessageRecord.canPromoteToFullyAutomatic(): Boolean {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = selectedDispatchText(),
            isUsingFallback = isUsingFallback,
        )
        return decision.approvalMode == ApprovalMode.FULLY_AUTO
    }

    data class Outcome(
        val updatedContacts: Int,
        val promotedMessages: Int,
        val skippedWithoutRoute: Int,
        val skippedNeedsReview: Int = 0,
    )
}
