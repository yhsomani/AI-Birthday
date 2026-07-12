package com.yashsomani.birthdayautopilot.contacts

import java.time.LocalDate

data class EnabledDestinationOccurrence(
  val contactId: String,
  val destination: CanonicalPhoneNumber,
  val occurrenceDate: LocalDate,
)

data class DuplicateDestinationConflict(
  val occurrenceDate: LocalDate,
  val destination: CanonicalPhoneNumber,
  val contactIds: Set<String>,
)

object DuplicateDestinationDetector {
  /** Returns every same-destination/same-civil-date conflict; every member must be blocked. */
  fun findConflicts(entries: Collection<EnabledDestinationOccurrence>): List<DuplicateDestinationConflict> =
    entries
      .groupBy { it.destination to it.occurrenceDate }
      .mapNotNull { (key, grouped) ->
        val contactIds = grouped.mapTo(sortedSetOf(), EnabledDestinationOccurrence::contactId)
        if (contactIds.size < 2) {
          null
        } else {
          DuplicateDestinationConflict(
            occurrenceDate = key.second,
            destination = key.first,
            contactIds = contactIds,
          )
        }
      }
      .sortedWith(compareBy(DuplicateDestinationConflict::occurrenceDate, { it.destination.value }))
}
