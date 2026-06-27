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

    override suspend fun getOccasionById(eventId: String): Occasion? {
        return eventDao.getById(eventId)?.toOccasion()
    }

    override suspend fun getOccasionTypeById(eventId: String): OccasionType? {
        return eventDao.getTypeById(eventId)?.let { OccasionType.fromRaw(it) }
    }

    override suspend fun getUpcomingPreviews(days: Int): List<UpcomingEventPreview> {
        return eventDao.getUpcoming(days, System.currentTimeMillis()).toUpcomingEventPreviews()
    }

    override suspend fun getNextUpcomingPreviewForContact(
        contactId: String,
        days: Int,
    ): UpcomingEventPreview? {
        return eventDao
            .getNextUpcomingForContact(contactId, days, System.currentTimeMillis())
            ?.toUpcomingEventPreview()
    }

    override suspend fun upsertOccasion(occasion: Occasion) = eventDao.upsert(occasion.toEventEntity())

    override suspend fun deactivateContactDerivedOccasion(contactId: ContactId, type: OccasionType) =
        eventDao.deactivateContactDerivedEvent(contactId.value, type.raw)
}
