# ADR 0002: Occasion Model

Date: 2026-06-27

Status: Accepted

## Context

The current app uses an `events` Room table for contact dates, manual events, and system-generated occasions. Phase 0 fixes improved compatibility but did not finish the target model:

- `EventEntity` stores rows in `events` with `type`, `label`, date fields, source, confidence, verification, and lifecycle flags.
- `PendingMessageEntity.eventId` references an event-like identifier and is unique with `(contactId, eventId, scheduledYear)`.
- `SentMessageEntity` now separates `eventId`, `occasionType`, and `occasionLabel`, while retaining legacy `eventType` as a compatibility alias.
- Holiday, revival, and follow-up workers now persist deterministic synthetic `EventEntity` rows before creating pending messages.
- `PLAN.md` Sections 9 and 10 define a target `Occasion` aggregate and an `occasions` table.

The remaining issue is conceptual: birthdays, anniversaries, holidays, revival prompts, and follow-ups are all occasions, but the current name and schema still center on `events`.

## Decision

The rebuild will use `Occasion` as the canonical domain concept and `occasions` as the target persistence table.

An occasion represents any relationship-relevant trigger that can produce reminders, drafts, follow-ups, notifications, or analytics:

- Birthday.
- Anniversary.
- Work anniversary.
- Graduation.
- Custom/manual date.
- Holiday.
- Revival.
- Follow-up.

Core identity rules:

- Every persisted message draft references an `OccasionId`.
- Canonical contact-derived occasions have deterministic ids derived from contact id and occasion type.
- Synthetic occasions have deterministic ids derived from their source and occurrence context, such as holiday/contact/year or follow-up/sent-message.
- Occasion type is a semantic enum and must not be overloaded with an id.
- Sent history stores both the resolved occasion id when known and semantic occasion type/label for stable analytics and display.

## Target Persistence

The target schema will add an `occasions` table or migrate `events` into an equivalent table with occasion terminology.

Minimum target columns:

- `id`
- `contactId`
- `type`
- `label`
- `dayOfMonth`
- `month`
- `year`
- `occurrenceYear` or equivalent generated occurrence key when needed
- `nextOccurrenceMs`
- `isActive`
- `notifyDaysBefore`
- `source`
- `confidenceScore`
- `isVerified`
- conflict metadata, either inline or in `occasion_conflicts`

The exact schema can add fields during the Phase 1 migration design, but it must preserve the identity and semantic split above.

## Migration Rules

From Room version 14:

- Copy `events` rows into `occasions`.
- Map `pending_messages.eventId` to the target `occasionId`.
- Map `sent_messages.eventId` to the target `occasionId` when present.
- Preserve `sent_messages.occasionType` and `sent_messages.occasionLabel`.
- Preserve legacy `sent_messages.eventType` only as a compatibility/read migration field until removed by a later explicit migration.
- Resolve or create synthetic occasions for deterministic `HOLIDAY_*`, `REVIVAL_*`, `FOLLOWUP_*`, and `FOLLOW_UP_*` identifiers.

## Consequences

Positive:

- Removes the event id versus event type ambiguity.
- Gives holiday, revival, and follow-up flows first-class persistence.
- Makes analytics, follow-up filtering, and message generation easier to reason about.

Costs:

- Requires Room schema migration and backup compatibility review.
- Requires repository and UI terminology cleanup.
- Requires mapper tests for all current event-generation paths.

## Verification

The decision is implemented when:

- No new code writes semantic occasion type into an id field.
- Pending drafts reference target occasion ids.
- Sent history contract tests cover resolved event/occasion ids, semantic types, and synthetic references.
- Version 14 fixture data migrates successfully.

