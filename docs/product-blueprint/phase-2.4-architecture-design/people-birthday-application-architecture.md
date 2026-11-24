# People and Birthday application architecture

**Design ID:** ARCH-PPL-BDY-001  
**Parent phase:** 2.4  
**Status:** Proposed target architecture; four product rules are approved, technical details remain subject to implementation review.

## 1. Bounded contexts and authority

| Context | Owns | Does not own |
|---|---|---|
| People | Person identity, profile, required birthday facts, preparation preference, validation and person lifecycle | Recurrence math, reminders, drafts, delivery proof |
| Birthday | Leap-day resolution, timezone interpretation, next occurrence and current-cycle reconciliation | Person identity/profile mutation, reminder offsets, draft content |
| Import adapters | CSV/Android parsing, source metadata, row diagnostics and candidate review | Direct persistence or rule-specific validation bypass |
| Reminder planning | Reminder offsets and scheduling requests derived from canonical occurrence data | Independent birthday date calculations |
| Presentation | Form state, candidate review, confirmation and accessibility | Transaction ownership or domain date rules |

Person is the profile root. Birthday occurrence is a separate domain representation derived from the Person's required birthday facts, the Person's leap-day selection and the current calendar context.

## 2. Target module boundaries

~~~mermaid
flowchart LR
  subgraph Presentation
    FORM[Person form]
    LIST[People list]
    PREVIEW[Import review]
  end
  subgraph Application
    UC[PersonLifecycleUseCases]
    INTAKE[PersonIntakeUseCase]
    DELETE[DeleteUndoUseCases]
    RECON[OccurrenceReconciliationUseCase]
  end
  subgraph Domain
    MODEL[Person + PersonDraft]
    VALID[PersonInputValidator]
    OCC[BirthdayOccurrenceService]
    POLICY[LeapDayPolicy + TimezonePolicy]
  end
  subgraph Data
    PORT[PersonRepository port]
    BPORT[BirthdayOccurrenceRepository port]
    UOW[UnitOfWork]
    DRIFT[Drift adapters]
  end
  subgraph Platform
    TZ[Device timezone adapter]
    CONTACTS[Android contacts adapter]
    REM[Reminder reconciliation adapter]
  end
  FORM --> UC
  LIST --> DELETE
  PREVIEW --> INTAKE
  UC --> VALID
  INTAKE --> VALID
  UC --> UOW
  INTAKE --> UOW
  DELETE --> UOW
  RECON --> OCC
  OCC --> POLICY
  POLICY --> TZ
  UOW --> PORT
  UOW --> BPORT
  PORT --> DRIFT
  BPORT --> DRIFT
  INTAKE --> CONTACTS
  UC --> RECON
  UC --> REM
~~~

Names are illustrative ports/use cases, not commitments to a particular file layout or class naming scheme.

## 3. Single write boundary for People

All writes that create, update, delete, restore or import Persons must enter through one People application boundary. The current dual-adapter arrangement is an implementation defect to remove in a separately authorized code phase.

Target responsibilities:

- PersonLifecycleUseCases: create, update, delete, undo/restore and query orchestration.
- PersonIntakeUseCase: consume normalized candidate data from manual, CSV and Android sources; validate; detect duplicate warnings; require explicit selection; issue writes through the same lifecycle boundary.
- PersonInputValidator: the canonical rule set for name, required birthday, birth year, contact fields, preferences, facts, notes and recipient timezone.
- PersonRepository: one canonical port for active lookup, include-deleted lookup, create/update, tombstone/restore and query semantics.
- BirthdayOccurrenceRepository: persistence of durable per-cycle occurrence records. Phase 2.4.1 recommends one stable occurrence identity per Person/year, unique on (personId, cycleYear); approval is pending under DEC-026/034.
- UnitOfWork: shares one local database transaction across Person and Birthday repositories.

No presentation widget or parser may write directly to a Person or Birthday table. A migration adapter may remain temporarily during cutover, but it cannot be a second long-term source of truth.

## 4. Canonical Person contract

### 4.1 Required fields and defaults

| Field | Target contract |
|---|---|
| id | Stable ID generated once per accepted create operation; retries reuse the operation identity |
| name | Required; canonical trim/length validation |
| birthdayMonth / birthdayDay | Both required; valid Gregorian month/day |
| birthYear | Optional; validated independently of recurrence |
| leapDayPolicy | Required only for Feb 29 birthdays; one of Feb 28 or Mar 1; not applicable to other dates |
| autoPrepare | false for new Person unless the user explicitly enables it |
| timezone | Optional recipient IANA timezone; when absent, runtime device-local timezone is used |
| createdAt / updatedAt | UTC timestamps from an injected clock |
| version | Increment once for every accepted Person mutation |
| deletedAt | Tombstone metadata; never cleared by an ordinary profile edit |

Other profile fields retain the Phase 2.3 contract and existing field limits until separately revised. Phone-number canonicalization remains open and must not be guessed.

### 4.2 Birthday requiredness across sources

- Manual create: form validation blocks save unless both month and day are valid.
- CSV: parse and validate every mapped value; any row without a valid birthday is not persistable.
- Android device contacts: missing/unparseable birthday creates an invalid candidate, not a saved Person. The UI may show the row with an error or omit it; DEC-030 remains open.
- Direct internal creation, tests, seeds and any future onboarding flow must enforce the same invariant.
- Editing an existing no-birthday legacy row must not silently preserve the invalid state on a successful write. Whether edit requires immediate birthday repair or presents a read-only/recovery route depends on DEC-023/035.
- No placeholder dates, fabricated years or inferred birthdays.

Birth year may remain null. Birthday month/day is the recurrence key; birth year affects age/milestone display only.

### 4.3 autoPrepare

The canonical new-record default is false across Person, PersonDraft, forms, CSV, Android candidates, factories and database defaults. Explicit opt-in can set true. A constructor default is a safety net, not a substitute for an explicit user preference in persisted data.

The migration must distinguish a genuinely saved true preference from values created only because one source path had a true default. That distinction is not currently observable in all rows; therefore treatment of historical true values remains open under DEC-031. Missing values resolve to false for new records.

## 5. Canonical birthday occurrence service

Only one domain service may calculate the next occurrence. It must accept:

- Person birthday month/day;
- Person leap-day policy when month/day is Feb 29;
- optional recipient IANA timezone;
- injected clock/reference instant;
- device-local timezone adapter for the fallback path.

It returns a typed result containing the resolved calendar date, cycle year, calendar context/timezone used, days-until/today result, leap-day adjustment and diagnostic status. It must not access Flutter widgets, Drift, reminder APIs or network services.

### 5.1 Leap-day rule

For Feb 29 Persons:
- In leap years, the resolved date is Feb 29.
- In non-leap years, use that Person's saved selection: Feb 28 or Mar 1.
- Do not use a global default for a Person whose selection is missing.
- An existing Feb 29 record without a selection must enter an explicit migration/remediation path before it can be treated as fully resolved. Whether it may be shown with a provisional date while awaiting choice is open under DEC-024.

### 5.2 Timezone rule

Resolution order:
1. If Person.timezone is present and is a valid IANA timezone, use it.
2. If Person.timezone is absent, use the device-local timezone.
3. No user-profile timezone layer is used.

The resolver must be called by manual create/edit, CSV/device import, lifecycle refresh, dashboard, calendar and reminder planning. A form helper or list widget may format a returned date but may not compute a different occurrence.

Invalid non-empty legacy timezone is not identical to a missing timezone. The target resolver must return a typed diagnostic rather than crash; whether invalid values fall back to device-local or require user repair is open (DEC-029). The device adapter must also expose a typed failure if timezone discovery is unavailable; fallback policy for that rare case remains open.

### 5.3 Refresh ownership

BirthdayLifecycleUseCase owns reconciliation of persisted current occurrence state. Triggers include Person create, birthday or timezone change, app resume, recipient/device date boundary, timezone setting change and restore completion.

The exact scheduling mechanism for many recipients in different timezones remains open (DEC-033). A single device-local midnight timer is not sufficient evidence that every recipient's calendar boundary has been handled.

Refresh is idempotent and must:
- batch-read existing occurrence data;
- use the one resolver;
- create or update according to the approved persistence strategy;
- preserve workflow state when the same cycle remains active;
- apply new-cycle behavior only on a genuine cycle transition;
- emit typed errors and repair diagnostics for duplicate/orphan records;
- never silently choose an arbitrary duplicate.

## 5.4 Per-cycle identity recommendation (Phase 2.4.1)

Recommend one durable Birthday identity per Person per calendar year. A cycle's identity is stable and must never be repurposed for the next year. A deterministic ID such as birthday-{personId}-{cycleYear} is one option; the database uniqueness guarantee is (personId, cycleYear), regardless of ID encoding.

On rollover:
- retain the previous cycle row and final workflow state;
- create or reconcile a new row for the new cycle;
- keep drafts and delivery evidence linked to the occurrence they belong to;
- do not reuse a stable per-Person Birthday ID across different years.

This recommendation aligns with one occurrence per Person per cycle. It requires approval under DEC-022/026/027 and a migration of all birthdayId references.


## 6. Application workflows

### 6.1 Create Person

1. Form submits a draft with an operation ID and a single captured clock instant.
2. Application service normalizes and validates the full input.
3. Missing/invalid birthday or missing Feb 29 choice blocks persistence.
4. The canonical occurrence service computes the first occurrence using recipient timezone or device-local fallback.
5. UnitOfWork opens one SQLite transaction.
6. The Person and required current-occurrence representation are written under the same transaction.
7. Commit occurs once.
8. A post-commit reconciliation request updates reminders and dependent read models. A scheduling failure does not undo valid Person data; it is visible and retryable.
9. The UI reports saved only after the transaction commits.

### 6.2 Edit Person

1. Load the active Person and its current version.
2. Validate the submitted complete profile; birthday cannot be cleared.
3. Determine whether birthday facts, leap-day preference or timezone changed.
4. Recompute through the canonical occurrence service when a relevant field changes.
5. In one transaction, update Person and occurrence consistently; preserve createdAt, increment version exactly once, and preserve fields not edited.
6. If optimistic concurrency is selected, reject stale versions with a conflict result rather than silently overwriting a newer edit. The exact conflict policy is open.
7. Reconcile reminders/read models after commit.

### 6.3 Import

1. Parser reads raw file/contact fields only and attaches source row/contact identity.
2. A shared mapper produces a typed candidate; it does not invent data.
3. The canonical validator checks every field, including birthday, birth year, phone, email, facts, notes and timezone.
4. Duplicate detection produces warnings with evidence, not identity assertions.
5. Preview requires user selection and clearly separates valid, invalid and possible-duplicate candidates.
6. Only valid, selected candidates enter the same PersonLifecycleUseCase as manual create.
7. Batch behavior defines per-row operation IDs and retry semantics. Whether one failed row aborts the whole batch or only that row is an open import policy.
8. Import summary reports created, skipped, invalid, duplicate-reviewed and failed counts without exposing sensitive data to logs.

### 6.4 Delete and Undo

Delete/Undo must be atomic from the user's perspective and implemented by one application service plus a local transaction. The service calculates a deletion plan, applies all selected row state changes in one transaction, and returns an undo token/expiry when restoration is available.

**Phase 2.4.1 recommendation:** soft-delete the Person as the aggregate visibility boundary; keep linked occurrence/draft/delivery rows hidden from active screens while Undo is available; Undo restores the same linked state. Permanent purge and the exact treatment of drafts, prior-cycle drafts, delivery evidence, reminders and pending jobs require explicit product/privacy approval under DEC-027. Do not implement irreversible cleanup or cascade deletion before approval.

A failed operation must not be swallowed or reported as success. Reminder/job cancellation occurs after commit and is retryable. Undo restores exactly the data declared recoverable by the approved deletion plan.

## 7. Read models and downstream consumers

People list, dashboard, calendar, Birthday stream and reminders consume the canonical occurrence/read model. They may apply display formatting and filters, but they must not:
- calculate recurrence with another helper;
- assume every Person uses device timezone;
- choose a leap-day rule independently;
- create occurrence records;
- silently substitute a missing birthday.

A legacy Person without a valid birthday must be displayed through an explicit migration/recovery state until DEC-023 is resolved. Normal new-record flows must never create this state.

## 8. Error model and observability

Use typed, non-PII errors for:
- missing/invalid birthday;
- missing Feb 29 choice;
- invalid recipient timezone;
- device timezone unavailable;
- duplicate current occurrence;
- orphan occurrence;
- aggregate transaction failure;
- stale update/conflict;
- post-commit reminder reconciliation failure;
- partial import row failure.

Logs and diagnostics must not contain Person name, birthday, phone, email, notes, facts, CSV cell content or draft text. Capture operation IDs, error category, entity count and timing only where privacy-approved.

## 9. Out of scope and dependencies

- Phone E.164 normalization and country selection are open; no country code may be guessed.
- Backup/sync serialization and tombstone conflict policy are outside this design.
- Message draft and delivery-history retention remain separately governed.
- Subscription/AI policy is unchanged.
- Any application change requires a separate authorization after DEC-023–035 are resolved or explicitly scoped as accepted deferrals.
