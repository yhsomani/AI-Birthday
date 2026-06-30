package com.example.domain.usecase

import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes dashboard metrics: health score (average), pending-review message count, upcoming events.
 * Returns a snapshot of the current dashboard state.
 */
@Singleton
class GetDashboardMetricsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(): DashboardMetrics {
        val contactProfiles = contactRepository.getHealthProfiles()
        val pendingCount = messageRepository.countPending().first()
        val upcomingEventsCount = eventRepository.countUpcoming(30)
        val healthScore = if (contactProfiles.isEmpty()) {
            0
        } else {
            contactProfiles.map { it.currentHealthScore }.average().toInt()
        }
        val sentCount = messageRepository.countAllSent().first()
        return DashboardMetrics(
            healthScore = healthScore,
            pendingCount = pendingCount,
            upcomingEventsCount = upcomingEventsCount,
            contactCount = contactProfiles.size,
            sentCount = sentCount
        )
    }

    fun observe(): Flow<DashboardMetrics> {
        return combine(
            contactRepository.getHealthProfilesFlow(),
            messageRepository.countPending(),
            eventRepository.countUpcomingFlow(30),
            messageRepository.countAllSent(),
        ) { contactProfiles, pendingCount, upcomingEventsCount, sentCount ->
            val healthScore = if (contactProfiles.isEmpty()) {
                0
            } else {
                contactProfiles.map { it.currentHealthScore }.average().toInt()
            }
            DashboardMetrics(
                healthScore = healthScore,
                pendingCount = pendingCount,
                upcomingEventsCount = upcomingEventsCount,
                contactCount = contactProfiles.size,
                sentCount = sentCount,
            )
        }
    }

    data class DashboardMetrics(
        val healthScore: Int,
        val pendingCount: Int,
        val upcomingEventsCount: Int,
        val contactCount: Int,
        val sentCount: Int
    )
}
