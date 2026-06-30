package com.example.data.repository

import com.example.core.db.dao.EventDao
import com.example.domain.event.toEventListItems
import com.example.domain.event.toEventEntity
import com.example.domain.event.toOccasion
import com.example.domain.event.toOccasions
import com.example.domain.event.toUpcomingEventPreview
import com.example.domain.event.toUpcomingEventPreviews
import com.example.domain.model.common.ContactId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview
import com.example.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao
) : EventRepository {

    override fun getOccasions(): Flow<List<Occasion>> {
        return eventDao.getAll().map { events -> events.toOccasions() }
    }

    override fun getEventListItems(): Flow<List<EventListItem>> {
        return eventDao.getAll().map { events -> events.toEventListItems() }
    }

    override suspend fun getOccasionsBefore(timeMs: Long): List<Occasion> {
        return eventDao.getEventsBefore(timeMs).toOccasions()
    }

    override suspend fun countUpcoming(days: Int): Int = eventDao.countUpcoming(days, System.currentTimeMillis())

    override fun countUpcomingFlow(days: Int): Flow<Int> {
        return getUpcomingPreviewsFlow(days).map { previews -> previews.size }
    }

    override suspend fun getOccasionById(eventId: String): Occasion? {
        return eventDao.getById(eventId)?.toOccasion()
    }

    override suspend fun getOccasionTypeById(eventId: String): OccasionType? {
        return eventDao.getTypeById(eventId)?.let { OccasionType.fromRaw(it) }
    }

    override fun getOccasionTypeByIdFlow(eventId: String): Flow<OccasionType?> {
        return eventDao.getAll().map { events ->
            events.firstOrNull { it.id == eventId }?.let { OccasionType.fromRaw(it.type) }
        }
    }

    override suspend fun getUpcomingPreviews(days: Int): List<UpcomingEventPreview> {
        return eventDao.getUpcoming(days, System.currentTimeMillis()).toUpcomingEventPreviews()
    }

    override fun getUpcomingPreviewsFlow(days: Int): Flow<List<UpcomingEventPreview>> {
        return eventDao.getAll().map { events ->
            events
                .asSequence()
                .filterUpcomingWithin(days)
                .sortedBy { it.nextOccurrenceMs }
                .toList()
                .toUpcomingEventPreviews()
        }
    }

    override suspend fun getNextUpcomingPreviewForContact(
        contactId: String,
        days: Int,
    ): UpcomingEventPreview? {
        return eventDao
            .getNextUpcomingForContact(contactId, days, System.currentTimeMillis())
            ?.toUpcomingEventPreview()
    }

    override fun getNextUpcomingPreviewForContactFlow(
        contactId: String,
        days: Int,
    ): Flow<UpcomingEventPreview?> {
        return eventDao.getAll().map { events ->
            events
                .asSequence()
                .filterUpcomingWithin(days)
                .filter { event -> event.contactId == contactId }
                .minByOrNull { it.nextOccurrenceMs }
                ?.toUpcomingEventPreview()
        }
    }

    override suspend fun upsertOccasion(occasion: Occasion) = eventDao.upsert(occasion.toEventEntity())

    override suspend fun deactivateContactDerivedOccasion(contactId: ContactId, type: OccasionType) =
        eventDao.deactivateContactDerivedEvent(contactId.value, type.raw)

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }

    private fun Sequence<com.example.core.db.entities.EventEntity>.filterUpcomingWithin(
        days: Int,
    ): Sequence<com.example.core.db.entities.EventEntity> {
        val nowMs = System.currentTimeMillis()
        val maxMs = nowMs + days.coerceAtLeast(0) * MILLIS_PER_DAY
        return filter { event ->
            event.isActive &&
                event.nextOccurrenceMs >= nowMs &&
                event.nextOccurrenceMs <= maxMs
        }
    }
}
