package com.example.domain.usecase

import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes dashboard metrics: health score (average), pending message count, upcoming events.
 * Returns a snapshot of the current dashboard state.
 */
@Singleton
class GetDashboardMetricsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(): DashboardMetrics {
        val contacts = contactRepository.getAllSync()
        val pending = messageRepository.getAllPending().first()
        val events = eventRepository.getUpcoming(30)
        val healthScore = if (contacts.isEmpty()) 0 else contacts.map { it.healthScore }.average().toInt()
        val sentCount = messageRepository.countAllSent().first()
        return DashboardMetrics(
            healthScore = healthScore,
            pendingCount = pending.size,
            upcomingEventsCount = events.size,
            contactCount = contacts.size,
            sentCount = sentCount
        )
    }

    data class DashboardMetrics(
        val healthScore: Int,
        val pendingCount: Int,
        val upcomingEventsCount: Int,
        val contactCount: Int,
        val sentCount: Int
    )
}
