package com.example.domain.usecase

import com.example.core.db.dao.RelationshipTypeCount
import com.example.core.db.entities.ContactEntity
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
        topHealthContactsProvider: suspend () -> List<ContactEntity>,
        neglectedContactsProvider: suspend () -> List<ContactEntity>
    ): Flow<AnalyticsSnapshot> = combine(
        messageRepository.countAllSent(),
        messageRepository.countPending(),
        contactRepository.countAll(),
        contactRepository.countByRelationshipType()
    ) { wishesSent, pending, totalContacts, relCounts ->
        AnalyticsSnapshot(
            totalWishesSent = wishesSent,
            pendingApprovals = pending,
            totalContacts = totalContacts,
            relationshipCounts = relCounts,
            topHealthContacts = topHealthContactsProvider(),
            neglectedContacts = neglectedContactsProvider()
        )
    }

    data class AnalyticsSnapshot(
        val totalWishesSent: Int,
        val pendingApprovals: Int,
        val totalContacts: Int,
        val relationshipCounts: List<RelationshipTypeCount>,
        val topHealthContacts: List<ContactEntity>,
        val neglectedContacts: List<ContactEntity>
    )
}
