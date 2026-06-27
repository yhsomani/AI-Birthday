package com.example.domain.repository

import com.example.domain.model.common.ContactId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun getOccasions(): Flow<List<Occasion>>
    fun getEventListItems(): Flow<List<EventListItem>>
    suspend fun getOccasionsBefore(timeMs: Long): List<Occasion>
    suspend fun countUpcoming(days: Int): Int
    suspend fun getOccasionById(eventId: String): Occasion?
    suspend fun getOccasionTypeById(eventId: String): OccasionType?
    suspend fun getUpcomingPreviews(days: Int): List<UpcomingEventPreview>
    suspend fun getNextUpcomingPreviewForContact(contactId: String, days: Int): UpcomingEventPreview?
    suspend fun upsertOccasion(occasion: Occasion)
    suspend fun deactivateContactDerivedOccasion(contactId: ContactId, type: OccasionType)
}
