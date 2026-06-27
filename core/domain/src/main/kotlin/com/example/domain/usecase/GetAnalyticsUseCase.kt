package com.example.domain.usecase

import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams analytics data: total wishes sent, pending approvals, total contacts,
 * and relationship-type breakdown. Combines 4 reactive DAO flows into a single
 * AnalyticsSnapshot stream for the AnalyticsScreen.
 */
@Singleton
class GetAnalyticsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository
) {
    operator fun invoke(
        topHealthLimit: Int = DEFAULT_CONTACT_RANKING_LIMIT,
        neglectedLimit: Int = DEFAULT_CONTACT_RANKING_LIMIT,
    ): Flow<AnalyticsSnapshot> = combine(
        messageRepository.countAllSent(),
        messageRepository.countPending(),
        contactRepository.countAll(),
        contactRepository.getRelationshipAnalyticsCounts()
    ) { wishesSent, pending, totalContacts, relCounts ->
        AnalyticsSnapshot(
            totalWishesSent = wishesSent,
            pendingApprovals = pending,
            totalContacts = totalContacts,
            relationshipCounts = relCounts,
            topHealthContacts = contactRepository.getTopHealthSummaries(topHealthLimit),
            neglectedContacts = contactRepository.getBottomHealthSummaries(neglectedLimit),
        )
    }

    data class AnalyticsSnapshot(
        val totalWishesSent: Int,
        val pendingApprovals: Int,
        val totalContacts: Int,
        val relationshipCounts: List<RelationshipAnalyticsCount>,
        val topHealthContacts: List<ContactAnalyticsSummary>,
        val neglectedContacts: List<ContactAnalyticsSummary>,
    )

    private companion object {
        const val DEFAULT_CONTACT_RANKING_LIMIT = 5
    }
}
