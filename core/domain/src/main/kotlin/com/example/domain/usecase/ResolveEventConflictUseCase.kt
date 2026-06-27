package com.example.domain.usecase

import com.example.domain.event.EventResolutionPolicy
import com.example.domain.event.toEventListItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.notification.buildEventReminderScheduleRequest
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
        val occasions = eventRepository.getOccasions().first()
        val selectedOccasion = occasions.firstOrNull { it.id.value == request.eventId && it.isActive }
            ?: return Outcome.EventNotFound
        val conflictGroup = EventResolutionPolicy.conflictGroupFor(
            occasions = occasions,
            selectedOccasion = selectedOccasion,
        )
        if (conflictGroup.size <= 1) return Outcome.NoConflict(selectedOccasion.toEventListItem())

        return when (request.action) {
            Action.MERGE_KEEP_SELECTED -> mergeKeepingSelected(selectedOccasion, conflictGroup)
            Action.KEEP_SEPARATE -> keepSeparate(selectedOccasion, conflictGroup)
        }
    }

    private suspend fun mergeKeepingSelected(
        selectedOccasion: Occasion,
        conflictGroup: List<Occasion>,
    ): Outcome.Resolved {
        val resolvedSelected = selectedOccasion.copy(
            source = EventResolutionPolicy.baseSource(selectedOccasion.source),
            isVerified = true,
        )
        eventRepository.upsertOccasion(resolvedSelected)
        eventReminderSchedulerService.scheduleReminder(buildEventReminderScheduleRequest(resolvedSelected))

        val deactivatedOccasions = conflictGroup.filterNot { it.id == selectedOccasion.id }
        deactivatedOccasions.forEach { occasion ->
            eventRepository.upsertOccasion(occasion.copy(isActive = false))
            eventReminderSchedulerService.cancelReminder(occasion.id.value)
        }

        return Outcome.Resolved(
            keptEvent = resolvedSelected.toEventListItem(),
            affectedEventIds = deactivatedOccasions.map { it.id.value },
            action = Action.MERGE_KEEP_SELECTED,
        )
    }

    private suspend fun keepSeparate(
        selectedOccasion: Occasion,
        conflictGroup: List<Occasion>,
    ): Outcome.Resolved {
        val resolvedOccasions = conflictGroup.map { occasion ->
            occasion.copy(
                source = EventResolutionPolicy.keepSeparateSource(occasion.source),
                isVerified = true,
            )
        }
        resolvedOccasions.forEach { eventRepository.upsertOccasion(it) }

        return Outcome.Resolved(
            keptEvent = resolvedOccasions.first { it.id == selectedOccasion.id }.toEventListItem(),
            affectedEventIds = resolvedOccasions.map { it.id.value },
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
            val keptEvent: EventListItem,
            val affectedEventIds: List<String>,
            val action: Action,
        ) : Outcome()

        data class NoConflict(val event: EventListItem) : Outcome()
        data object EventNotFound : Outcome()
    }
}
