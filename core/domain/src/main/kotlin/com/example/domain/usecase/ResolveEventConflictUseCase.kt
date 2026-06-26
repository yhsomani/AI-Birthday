package com.example.domain.usecase

import com.example.core.db.entities.EventEntity
import com.example.domain.event.EventResolutionPolicy
import com.example.domain.repository.EventRepository
import com.example.domain.service.EventReminderSchedulerService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveEventConflictUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventReminderSchedulerService: EventReminderSchedulerService,
) {
    suspend operator fun invoke(request: Request): Outcome {
        val events = eventRepository.getAll().first()
        val selectedEvent = events.firstOrNull { it.id == request.eventId && it.isActive }
            ?: return Outcome.EventNotFound
        val conflictGroup = EventResolutionPolicy.conflictGroupFor(events, selectedEvent)
        if (conflictGroup.size <= 1) return Outcome.NoConflict(selectedEvent)

        return when (request.action) {
            Action.MERGE_KEEP_SELECTED -> mergeKeepingSelected(selectedEvent, conflictGroup)
            Action.KEEP_SEPARATE -> keepSeparate(selectedEvent, conflictGroup)
        }
    }

    private suspend fun mergeKeepingSelected(
        selectedEvent: EventEntity,
        conflictGroup: List<EventEntity>,
    ): Outcome.Resolved {
        val resolvedSelected = selectedEvent.copy(
            source = EventResolutionPolicy.baseSource(selectedEvent.source),
            isVerified = true,
        )
        eventRepository.upsert(resolvedSelected)
        eventReminderSchedulerService.scheduleReminder(resolvedSelected)

        val deactivatedEvents = conflictGroup.filterNot { it.id == selectedEvent.id }
        deactivatedEvents.forEach { event ->
            eventRepository.upsert(event.copy(isActive = false))
            eventReminderSchedulerService.cancelReminder(event.id)
        }

        return Outcome.Resolved(
            keptEvent = resolvedSelected,
            affectedEventIds = deactivatedEvents.map { it.id },
            action = Action.MERGE_KEEP_SELECTED,
        )
    }

    private suspend fun keepSeparate(
        selectedEvent: EventEntity,
        conflictGroup: List<EventEntity>,
    ): Outcome.Resolved {
        val resolvedEvents = conflictGroup.map { event ->
            event.copy(
                source = EventResolutionPolicy.keepSeparateSource(event.source),
                isVerified = true,
            )
        }
        resolvedEvents.forEach { eventRepository.upsert(it) }

        return Outcome.Resolved(
            keptEvent = resolvedEvents.first { it.id == selectedEvent.id },
            affectedEventIds = resolvedEvents.map { it.id },
            action = Action.KEEP_SEPARATE,
        )
    }

    data class Request(
        val eventId: String,
        val action: Action,
    )

    enum class Action {
        MERGE_KEEP_SELECTED,
        KEEP_SEPARATE,
    }

    sealed class Outcome {
        data class Resolved(
            val keptEvent: EventEntity,
            val affectedEventIds: List<String>,
            val action: Action,
        ) : Outcome()

        data class NoConflict(val event: EventEntity) : Outcome()
        data object EventNotFound : Outcome()
    }
}
