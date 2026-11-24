# Birthday lifecycle contract

**Specification ID:** BDY-SPEC-001  
**Domain:** Birthday recurrence and occurrence lifecycle  
**Requirements:** FR-006–FR-008 and FR-010; PRD-002–PRD-003  
**Source review commit:** 93e803aee3064b046ac9b9093cfd35e4b1a0ae62  
**Status:** Source-backed specification; leap-day policy, timezone precedence and aggregate integrity require approval  
**Scope:** Gregorian recurrence, next occurrence, one active occurrence per Person, lifecycle refresh, date/timezone boundaries, status and draft linkage.

Evidence labels: **FACT** is current repository behavior. **PROPOSED CONTRACT** is desired behavior to approve. **OPEN** identifies unresolved policy. This document is not evidence of physical-device behavior.

## 1. Purpose and boundary

The Birthday domain converts the month/day stored on a Person into a concrete upcoming calendar date and maintains the current occurrence consumed by dashboard, calendar, reminders, message preparation and history.

It owns Gregorian month/day validation; next occurrence computation; leap-day resolution; recipient calendar timezone interpretation; occurrence identity/current-cycle refresh; and Birthday status plus active-draft association.

It does not own Person identity/profile editing, reminder offsets or Android alarm delivery, message revision content, delivery handoff/user confirmation, or account backup authorization.

**FACT:** The normal lifecycle path is Birthday stream provider → fetch active People → BirthdayLifecycleService.refresh → BirthdayEngine.computeNext → save/update a Birthday row per Person → expose the Birthday repository stream.

## 2. Core entity and invariants

### 2.1 Birthday occurrence record

| Field | Meaning | Current storage |
|---|---|---|
| id | Stable occurrence record ID | Text primary key |
| personId | Associated Person ID | Text; no declared FK |
| cycleYear | Calendar year of occurrence | Integer |
| date | Resolved occurrence date | DateTime |
| status | Current occurrence lifecycle status | Text enum name |
| draftId | Optional ID of active linked draft | Nullable text |
| createdAt / updatedAt | Occurrence timestamps | DateTime |

**FACT:** Birthday has a primary key on id and an index on person_id, but no unique constraint on personId and no foreign key to Persons. The lifecycle service detects duplicate Birthday rows for one personId and throws StateError; it does not repair duplicates.

### 2.2 Required invariants

**PROPOSED CONTRACT:**
1. A Person may have no Birthday occurrence when no valid birthday exists.
2. A Person with a valid birthday has exactly one current occurrence at a time.
3. The occurrence ID remains stable when the cycle advances; current code uses the pattern birthday-{personId}.
4. cycleYear and date refer to the same resolved calendar cycle.
5. Re-running refresh with unchanged Person birthday, timezone and reference calendar date is idempotent.
6. A cycle rollover invalidates the active draft link; prior draft/history retention is governed by a separate explicit rule.
7. A completed status remains completed for the same cycle unless a separate user action changes it.
8. Duplicate active occurrence records are a data-integrity error, not a reason to silently choose one.

## 3. Calendar calculation contract

### 3.1 Gregorian calendar

**FACT:** BirthdayEngine implements Gregorian leap-year logic: divisible by 4 is leap; century years are not leap unless divisible by 400. It returns days-in-month and rejects invalid month/day combinations. When birth year is unknown, Feb 29 is accepted because it can occur in a future leap year.

### 3.2 Next occurrence algorithm

**FACT:** BirthdayEngine.computeNext:
1. accepts month/day and a reference value;
2. when recipient timezone is supplied, converts the instant into that IANA timezone;
3. otherwise uses the caller's local calendar frame (a UTC reference is converted to local first);
4. resolves the birthday date in the reference year;
5. advances to the next year if the resolved date is before the reference calendar date;
6. calculates whole calendar days by comparing normalized UTC calendar dates, avoiding daylight-saving-hour drift;
7. returns isToday, daysUntil, resolved year/date and whether Feb 29 was adjusted.

A birthday on the reference calendar day is today with daysUntil = 0. A birthday earlier in the year rolls to the next calendar year.

birthYear is accepted by computeNext but does not change recurrence calculation. The UI uses it to calculate “turns N” when a known birth year exists.

### 3.3 Leap day

**FACT:** LeapDayResolution.feb28 is the default. In a non-leap year, Feb 29 resolves to Feb 28. LeapDayResolution.mar1 is also implemented and tested. In leap years, it remains Feb 29.

**OPEN:** Product governance must decide whether Feb 28 is the fixed product rule or the app should expose a user preference. Source has an alternate calculation parameter, but Person has no persisted leap-day preference and the UI does not expose one. Do not imply a per-recipient preference exists.

### 3.4 Timezone precedence and boundaries

**FACT:** If Person.timezone is non-empty, the engine uses it. If null, the engine uses device-local calendar frame. BirthdayEngine.effectiveTimezone(recipientTimezone, userTimezone) exists and implements recipient → user → UTC precedence, but inspected lifecycle and People-list call sites pass only recipient timezone. They do not wire a user timezone into the helper. The effective fallback at those call sites is therefore device-local, not a stored user preference.

**FACT:** birthdaysStreamProvider schedules a refresh at next device-local midnight. That is not midnight for every recipient timezone.

**PROPOSED CONTRACT:**
- Approve one explicit precedence rule: recipient IANA timezone → user-selected IANA timezone → documented device-local fallback (or UTC, if product chooses that).
- Use the same resolver in People list, Birthday lifecycle, Dashboard, Calendar, Reminder scheduler and import flows.
- Recompute on app resume, relevant date boundary and device/timezone setting changes.
- If multiple recipient timezones are supported, trigger refresh for each active recipient timezone or guarantee recomputation at every consumer boundary.
- Invalid stored timezone must be recovered gracefully; it must not crash a stream.
- DST changes alter instant offsets, not the birthday's calendar month/day or whole-day countdown.

### 3.5 Import/edit discrepancy

**FACT:** Birthday.nextBirthdayDate is a separate timezone-free static helper. Person form save and CSV/device import use this helper to create Birthday rows, while normal stream refresh uses BirthdayEngine.computeNext with optional recipient timezone. Initial saved date can therefore be calculated in a different calendar frame from a later lifecycle refresh.

**PROPOSED CONTRACT:** Every write path must call one canonical occurrence service. UI, manual create/edit, CSV/device import and refresh must not independently implement occurrence calculation.

## 4. Occurrence lifecycle and status

### 4.1 Current status values

BirthdayStatus values are: created, upcoming, reminderDue, messageNotPrepared, messageDrafted, messageReviewed, readyForDelivery, handedOff, completed and failed.

isActionNeeded currently returns true for reminderDue, messageNotPrepared, messageDrafted and failed. It returns false for messageReviewed, readyForDelivery, handedOff, completed, created and upcoming.

### 4.2 Current refresh behavior

BirthdayLifecycleService.refresh:
1. reads all Birthday rows once and indexes them by personId;
2. throws StateError if more than one row exists for the same Person;
3. skips Persons without valid birthday month/day;
4. computes the next occurrence for each Person with a birthday;
5. creates a row if none exists, using the stable birthday-{personId} ID pattern;
6. sets reminderDue if today, otherwise upcoming;
7. when stored cycle/date matches, preserves status except created/upcoming are promoted to reminderDue if today;
8. when cycle/date changes, updates the row, resets status to reminderDue/upcoming and clears draftId.

**FACT:** For an unchanged cycle, completed status and current draftId are preserved. On rollover, draftId is cleared. The old MessageDraft row itself is not deleted by this service.

### 4.3 Target state behavior

**PROPOSED CONTRACT:**
- Lifecycle refresh may create/update occurrence date and cycle and promote date-derived states only.
- Workflow states (messageDrafted, messageReviewed, readyForDelivery, handedOff, completed, failed) must not be overwritten by routine refresh on the same cycle.
- A new cycle starts upcoming or reminderDue based on recipient calendar day, with no active draft link.
- Message preparation/delivery domains own their transitions and validate them.
- UI must not infer status from navigation, app launch or AI generation alone.
- Failure reason and retryability must be stored separately or as a structured event, not overloaded into one generic failed state.

### 4.4 Transition table

| Current state | Trigger | Target state | Owner / rule |
|---|---|---|---|
| No row | Person with valid birthday reconciled | upcoming / reminderDue | Birthday lifecycle |
| created | Same cycle is birthday-today | reminderDue | Birthday lifecycle |
| upcoming | Same cycle is birthday-today | reminderDue | Birthday lifecycle |
| reminderDue | Date refresh, same cycle | reminderDue | Preserve |
| messageNotPrepared | Date refresh, same cycle | messageNotPrepared | Preserve; message workflow decides |
| messageDrafted | Date refresh, same cycle | messageDrafted | Preserve; message workflow decides |
| messageReviewed | Date refresh, same cycle | messageReviewed | Preserve |
| readyForDelivery | Date refresh, same cycle | readyForDelivery | Preserve |
| handedOff | Date refresh, same cycle | handedOff | Preserve; external outcome remains unconfirmed |
| completed | Date refresh, same cycle | completed | Preserve |
| failed | Date refresh, same cycle | failed | Preserve until recovery action |
| Existing row | Computed cycle/date differs | upcoming / reminderDue | New cycle; clear active draft link |
| Any row | Person removed | No active row | Person aggregate delete policy |

This transition table is a proposed contract; map it to actual message and delivery writers before implementation.

## 5. Refresh triggers and failure behavior

**FACT:** birthdaysStreamProvider invokes refresh before subscribing to Birthday rows and uses a timer to invalidate itself at next local midnight. BirthdayLifecycleService loads all existing rows once rather than one query per Person. Existing tests assert one batch read and zero per-Person lookups.

**PROPOSED CONTRACT:**
- Reconcile when Person is created, birthday changes, timezone changes, app resumes, date boundary is reached, backup restore completes, or timezone database data changes.
- A failed refresh must not emit false success or leave stale data indefinitely. Expose last reconciliation error and retry path.
- Bulk refresh remains O(P + B) and avoids N+1 database reads; preserve query-count assertions at scale.
- Refresh is idempotent under repeated provider rebuilds and duplicate event delivery.
- Duplicate records require explicit diagnostics/repair; do not silently delete an arbitrary row.
- Person without birthday must not create an occurrence or reminder.

## 6. Data integrity and aggregate boundaries

**FACT:** Birthdays.personId is indexed but not unique and has no declared FK. Birthday has no version or tombstone. Persons has version and tombstone; Birthday does not. Person form writes Person first and Birthday second. On create, a Birthday write failure attempts to soft-delete the newly created Person. On edit, Birthday write failure can leave the Person edit saved while Birthday write fails. These writes are not one transaction.

People delete also soft-deletes Person and hard-deletes Birthday separately; see the [People lifecycle contract](people-lifecycle-contract.md).

**PROPOSED CONTRACT:**
- Person + Birthday create/edit/delete commits atomically when user action promises both.
- Enforce one current Birthday per Person with a unique database constraint or transactionally guaranteed repository invariant.
- Decide whether Birthday is derived cache/materialization or durable user state. If derived, reconstruct deterministically from Person; if durable, define migration/sync/tombstone policy.
- Define referential behavior for drafts, reminders, delivery and jobs when Person/Birthday is removed.
- Restore must not reset delivery evidence or revive completed cycles accidentally.

## 7. Reminder and downstream integration

Birthday occurrence is the canonical date input for reminder planning, dashboard, calendar and Message Studio. Reminders must derive offsets from resolved occurrence and recipient timezone, not recompute month/day independently.

**PROPOSED CONTRACT:**
- Scheduler receives resolved occurrence ID/date/timezone/status and reminder settings.
- Rescheduling cancels obsolete alarms before scheduling replacements.
- Permission denied or exact-alarm unavailable does not block People/Birthday viewing or manual Message Studio use.
- Device timezone change must not silently shift a recipient birthday to another calendar date.
- Message preparation and delivery may update workflow status, but date refresh cannot overwrite those workflow states on the same cycle.

## 8. Edge-case matrix

| Scenario | Expected contract outcome | Current source/test evidence |
|---|---|---|
| Invalid month/day | Reject before persistence | Validator and engine tests |
| Feb 29 in leap year | Feb 29 | Engine tests |
| Feb 29 in non-leap year | Default Feb 28; Mar 1 is alternate parameter | Engine and lifecycle tests |
| Birthday equals reference date | isToday true, daysUntil zero | Engine tests |
| Birthday already passed this year | Resolve next year | Engine tests |
| Recipient timezone differs from device | Compute in recipient timezone | Engine tests |
| Recipient timezone absent | Device-local calendar currently used | Source-reviewed |
| Invalid imported timezone | Reject/quarantine gracefully | Validator exists; import path gap |
| App crosses device-local midnight | Refresh stream at local midnight currently | Provider source; runtime timer test needed |
| App crosses recipient midnight before device midnight | Refresh recipient-specific occurrence | Not covered by current local-midnight timer |
| Duplicate Birthday rows per Person | Visible integrity error and repair flow | Current StateError and duplicate test |
| Same active cycle is completed | Keep completed | Lifecycle test |
| New cycle reached | Reset current status and clear active draft link | Lifecycle test |
| Person has no birthday | No occurrence created | Refresh skip path; focused test missing |
| Birthday delete fails during Person delete | Keep aggregate consistent or report partial failure | Current code swallows; missing test |
| Draft linked to prior cycle | Explicit archive/delete/history policy | Current rollover clears link but retains row |

## 9. Open product/architecture decisions

| ID | Question | Evidence / current behavior | Owner |
|---|---|---|---|
| BDY-DEC-001 | Feb 28 fixed policy or user-selectable Feb 28 / Mar 1? | Feb 28 default; Mar 1 parameter; no Person preference | Product |
| BDY-DEC-002 | Recipient → user timezone → device local, or recipient → device local? | Helper supports user timezone but inspected callers do not pass one | Product |
| BDY-DEC-003 | Keep one Birthday row updated each year or immutable row per cycle? | Current service keeps one row per Person and overwrites cycle/year/date | Product / Data |
| BDY-DEC-004 | What happens to prior-cycle drafts and delivery history at rollover? | Birthday draft link clears; draft record remains | Product / Delivery |
| BDY-DEC-005 | Recovery for duplicate Birthday rows? | Current service throws | Data |
| BDY-DEC-006 | Which app lifecycle/timezone events trigger refresh? | Timer uses device-local midnight; other events rebuild provider | Android / Birthday |
| BDY-DEC-007 | Is Birthday a derived cache or durable sync entity? | Persisted row with no version/tombstone | Data / Sync |

## 10. Source inventory

- lib/features/birthdays/domain/birthday_engine.dart — Gregorian rules, leap-day resolution and timezone-aware next occurrence.
- lib/features/birthdays/domain/models/birthday.dart — occurrence data and status enum.
- lib/features/birthdays/application/birthday_lifecycle_service.dart — current-cycle reconciliation.
- lib/features/birthdays/domain/repositories/birthdays_repository.dart — repository contract and in-memory implementation.
- lib/core/database/app_database.dart — Birthday table and index definition.
- lib/core/database/drift_repositories.dart — persisted Birthday queries and writes.
- lib/app/providers.dart — Birthday stream, reconciliation and local-midnight timer.
- lib/features/people/presentation/person_form_screen.dart — manual Person/Birthday write orchestration.
- lib/features/people/presentation/people_screen.dart — import/delete Birthday write orchestration.

Related: [People lifecycle contract](people-lifecycle-contract.md) · [Acceptance criteria and traceability](acceptance-criteria-and-traceability.md).
