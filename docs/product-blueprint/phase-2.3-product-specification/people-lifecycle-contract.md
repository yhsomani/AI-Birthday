# People lifecycle contract

**Specification ID:** PPL-SPEC-001  
**Domain:** People / recipient management  
**Requirements:** FR-001–FR-005; PRD-001  
**Source review commit:** 93e803aee3064b046ac9b9093cfd35e4b1a0ae62  
**Status:** Source-backed specification with Phase 2.3.1 approvals overlaid; target architecture and implementation remain pending  
**Scope:** Local recipient creation, editing, retrieval, deletion/recovery, CSV/device-contact intake, export, and birthday/reminder side effects.

Evidence labels: **FACT** means directly present in reviewed source or tests. **PROPOSED CONTRACT** is behavior recommended for approval before implementation. **OPEN** means a product or domain owner must decide. This document does not authorize code changes.

## 1. Purpose and boundaries

A Person is the user-owned recipient profile that holds the minimum information needed to remember a person and prepare an appropriate birthday greeting. A Person is not a social-network contact, a device address-book mirror, or a delivery receipt.

The People domain owns recipient identity and user-entered context; required birthday month/day and optional birth year; relationship, tone, language, contact and timezone preferences; recipient-level preparation preferences and delivery policy; validation and the person sync envelope; and user-initiated import/export and duplicate review.

It does not own birthday recurrence calculation, reminder scheduling mechanics, message content or AI entitlement, proof a message was sent/received, or cloud backup authorization.

**FACT:** Core People persistence is local Drift/SQLite. The list stream and import/export path use DriftPeopleRepository, while the form's PersonService uses a separate DriftPeopleStore adapter against the same Persons table. Device contacts are read through the Android method channel named com.yashsomani.ai_birthday/contacts.

## 2. Actors, authority and ownership

| Actor | Allowed actions | Authority boundary |
|---|---|---|
| Local app user | Create, view, edit, search, import, export, delete and undo recipients | Only explicit user actions change saved recipient data |
| Android Contacts provider | Supply candidate name, phone and birthday after OS permission | Read-only source; never an automatic write or merge authority |
| CSV input | Supply candidate rows for parsing and review | Untrusted input; never a direct database import |
| People application service | Validate normal form writes; create IDs and timestamps; increment version; request reminder reconciliation | Does not own birthday recurrence or external provider actions |
| Drift/SQLite | Persist Person rows and tombstones | Local operational source of truth for core workflows |
| Birthday domain | Create/reconcile current occurrence from Person birthday fields | Person identity and birthday values are inputs; date rules live in BirthdayEngine |

**PROPOSED CONTRACT:** Imported values remain candidates until the user confirms selected rows. An import source must not silently overwrite an existing Person. If a candidate appears to match an existing record, show the evidence and let the user decide. “Potential duplicate” is not proof of identity.

## 3. Canonical Person data contract

The current model in lib/features/people/domain/models/person.dart contains:

| Field | Required? | Current type / default | Contract meaning |
|---|---|---|---|
| id | Yes | String | Stable local identity; normal create generates UUID |
| name | Yes | String | Display name; form captures trimmed text |
| birthdayMonth | Optional for imported contacts; required by manual form | Nullable integer | Gregorian month 1–12 |
| birthdayDay | Optional for imported contacts; required by manual form | Nullable integer | Valid day for selected month |
| birthYear | No | Nullable integer | User-provided year for age/milestone display; not required for recurrence |
| phoneNumber | No | Nullable string | Contact detail; current code validates shape but does not consistently canonicalize it |
| email | No | Nullable string | Optional contact detail |
| relationship | No | Enum; defaults to friend | Relationship category |
| relationshipCloseness | No | Enum; defaults to casual | Closeness descriptor |
| preferredLanguage | No | String; defaults to en | Language preference |
| preferredTone | No | Enum; defaults to warm | Greeting tone |
| importantFacts | No | String list; defaults empty | Facts explicitly entered by the user |
| notes | No | Nullable string | User's private notes |
| preferredDeliveryChannel | No | Enum; defaults to WhatsApp | Preferred handoff channel, not delivery evidence |
| timezone | No | Nullable string | Optional IANA timezone for this recipient |
| autoPrepare | No | Boolean | Preparation preference; current defaults are inconsistent |
| autoSendPolicy | No | Enum; defaults to manualOnly | Delivery policy; not permission to bypass review or distribution controls |
| createdAt | Yes | DateTime | Creation timestamp; Drift adapters persist UTC |
| updatedAt | Yes | DateTime | Last accepted mutation timestamp |
| version | Yes | Integer; defaults to 1 | Person-level sync version |
| deletedAt | No | Nullable DateTime | Tombstone; non-null rows are excluded from active-list queries |

### 3.1 Birthday requiredness — approved in Phase 2.3.1

**FACT — source baseline:** The Person model and some intake paths permit a Person without a birthday. Manual create and CSV parsing require month/day, while Android device-contact candidates may omit it. Existing persisted no-birthday records are therefore possible.

**APPROVED PRODUCT DECISION:** Birthday month/day is required for every newly persisted Person, regardless of entry path. There is no “Save without birthday” path. Birth year remains optional. A candidate with missing, partial or impossible month/day must not be persisted.

**ARCHITECTURE CONSEQUENCES:**
- Manual form, CSV import and device-contact import use one canonical validator before any write.
- A missing/invalid birthday is surfaced as a validation issue; exact preview presentation (show as invalid candidate or exclude before preview) remains open.
- A Person without a birthday found in existing storage is a legacy-data exception. Do not invent a date, silently delete the row or treat the old source behavior as the target contract.
- Read compatibility and the remediation/migration UX for those existing rows remain open.
- Person profile facts and the current Birthday occurrence remain separate concepts; a persisted Person with valid birthday facts must reconcile to one current occurrence under the approved cycle strategy.

### 3.2 Constructor/default discrepancy

**FACT:** Person.autoPrepare defaults to true; PersonDraft.autoPrepare defaults to false; the Drift column default is false; the add/edit form initializes it to true; ParsedContactCandidate defaults to true; CSV parsing defaults it to true when absent.

**APPROVED PRODUCT DECISION:** autoPrepare defaults to false for every newly created Person and every intake path. Explicit user opt-in may set true. Implementation must align model, draft, form, import candidate, CSV fallback and database defaults. Treatment of existing stored true values and the exact migration behavior remain open.

### 3.3 Phone-number contract

**FACT:** The validator accepts strings matching the current 6–20-character phone regex. The normal save path trims surrounding whitespace but does not convert numbers to E.164. Duplicate comparison strips non-digits; this is not country-aware normalization. Some documentation describes numbers as normalized more strongly than the implementation supports.

**PROPOSED CONTRACT:** Store a canonical international number only after country context is explicit. Reject ambiguous local numbers rather than guessing a country code. Digits-only equality must not be treated as conclusive identity.

## 4. Validation contract: current source-backed baseline

Normal Person form/service writes use PersonInputValidator.

| Field | Current validation | Failure behavior |
|---|---|---|
| Name | Trimmed value must be non-empty and at most 120 characters | Field error; no save |
| Birthday | Month/day must both exist for every create and every persisted Person update; impossible Gregorian dates are rejected | Required/invalid birthday field error; no write |
| Birth year | Optional; 1900–2199 inclusive | Invalid birth year field error |
| Phone | Optional; when supplied, 6–20 characters from digits, plus, hyphen, parentheses, period and space | Invalid phone field error |
| Email | Optional; max 254 characters and a simple email-shape regex | Invalid email field error |
| Relationship | Enum-backed; current validator checks enum name length | Relationship parser falls back to default for unknown values; this needs explicit compatibility policy |
| Language | Non-empty, at most 10 characters | Language field error |
| Important facts | Empty facts removed; maximum 20; each at most 200 characters | Facts field error |
| Notes | Optional; trimmed content at most 2,000 characters | Notes field error |
| Timezone | Optional; must resolve in IANA timezone database and be at most 64 characters | Invalid timezone field error |

Birthday validation delegates to BirthdayEngine.isValidMonthDay. Feb 29 is accepted even when the known birth year is a non-leap year because the recurrence engine resolves the celebration date separately.

**Approved intake constraint:** This validation applies to manual entry, CSV, device contacts and any future create/import path. Existing null-birthday rows are legacy exceptions and require a separately approved remediation plan.


**PROPOSED CONTRACT:**
- Every intake channel must use one canonical validator/normalizer before persistence.
- A partial month/day pair is always invalid.
- Blank optional strings become null; facts are trimmed and empty facts removed.
- Invalid timezone values must not reach BirthdayEngine.computeNext, which can throw when resolving an unknown zone.
- Unknown enum values in historical data need an explicit compatibility policy; silent replacement is not acceptable for user-selected preferences.
- A failed save retains form contents and exposes a retryable error without implying that Person or Birthday was committed.

## 5. Person lifecycle

### 5.1 Create

**Current form flow:** User opens the add form, enters a trimmed name and birthday month/day, optionally opens relationship/tone, contact/delivery, facts and notes sections, validates, then calls PersonService.create. The service assigns UUID, timestamps and version 1, writes Person, logs the Person ID and requests reminder reconciliation. The form then calculates and writes the related Birthday separately.

**PROPOSED CONTRACT:**
1. Generate a stable ID once; retries must not create duplicate Persons.
2. Validate the complete draft before writing.
3. Persist Person and its initial occurrence as one logical aggregate operation.
4. Do not schedule reminders from a partially committed Person/Birthday pair.
5. On failure, retain the draft and report whether nothing was saved or recovery is required.

### 5.2 Read, search and list

**FACT:** Active People streams exclude tombstoned rows. The list sorts case-insensitively by name and then by ID. Search uses a case-insensitive regular expression against the name. A Person without a birthday displays “No birthday set” and no countdown.

**PROPOSED CONTRACT:** Search should be literal substring matching, not user-controlled regular-expression behavior; use stable ordering and loading/empty/no-match/error states. Ordinary journeys must never return soft-deleted rows. A separate recovery surface is required before tombstones can be restored outside the immediate Undo action.

### 5.3 Edit

**Current form flow:** The form loads an existing Person and populates editable fields. It marks the form dirty on input changes, validates, then calls PersonService.update. The form trims name, phone, email, relationship text, notes, timezone and facts. The service increments version and updates updatedAt; createdAt is preserved.

**FACT:** Existing birthday holders cannot clear month/day through the normal edit flow because birthday is required when the existing Person already has one. A contact with no birthday can be edited without adding one.

**PROPOSED CONTRACT:**
- Preserve ID, creation timestamp and tombstone state on ordinary edit.
- Increment version exactly once for a successful mutation; failed validation/writes do not increment it.
- Preserve every field not explicitly edited.
- If birthday removal is supported, make it an explicit action explaining reminder/occurrence/draft effects; never silently interpret a blank field.
- Dirty-form navigation asks before discard. Keep Editing preserves input; Discard returns without a write.
- Concurrent update policy must be explicit. Last-write-wins without conflict detection is not yet specified.

### 5.4 Delete and Undo

**Current behavior:** The People screen asks for confirmation. It reads the associated Birthday, calls PersonService.remove to tombstone Person and increment version, then separately attempts a hard delete of the Birthday row. The Birthday delete exception is swallowed. A snackbar offers Undo; Undo restores Person and re-saves the previously read Birthday. Restore exceptions are swallowed. Tests cover the normal Undo path and the Person-service failure path.

**Integrity risks:**
- Person tombstone and Birthday hard-delete are not one database transaction.
- Failure deleting Birthday can leave an orphaned Birthday row.
- Failure restoring Person is swallowed while Birthday may still be re-created.
- Associated message drafts are not deleted in the same operation. A later orphan-draft sweep removes drafts whose Birthday row no longer exists, but that is not an atomic part of delete.
- The current schema declares no foreign-key cascade for Person → Birthday.

**PROPOSED CONTRACT:**
1. Confirmation describes exactly which data is hidden, retained or removed.
2. Delete operates on the Person/Birthday/Draft aggregate with one transaction or a durable recoverable deletion operation.
3. Undo is time-bounded and restores the exact pre-delete aggregate or displays a partial-recovery error.
4. A failed delete leaves the complete original aggregate readable.
5. A successful delete removes Person from active lists and prevents reminders/jobs from firing for it.
6. A successful Undo restores Person, current Birthday and intended draft/history links without resetting delivery evidence.
7. No swallowed exception may be reported as successful delete or restore.

## 6. Import and export

### 6.1 CSV import

**FACT:** Import is initiated by pasting CSV. The parser supports header mapping and legacy column positions, skips malformed rows, detects potential duplicates against current People and within the batch, and returns candidates plus invalid-row messages. The preview starts with non-duplicates selected; potential duplicates are unselected by default but can be manually selected. Confirmed rows are inserted as new records, not merged.

Duplicate signals currently include same normalized name plus same cleaned phone digits, same cleaned phone digits, same name plus same birthday month/day, and same name alone. A duplicate warning is informational, not proof of identity.

**FACT:** CSV import currently requires a valid birthday and skips missing/impossible dates. It does not pass every imported value through PersonInputValidator. Phone and timezone can bypass normal form validation. A malformed timezone may later break timezone-based date calculation.

**PROPOSED CONTRACT:**
- Parse, normalize, validate and classify candidates before user confirmation.
- Show exact reasons for invalid rows and duplicate warnings.
- Never write until the user confirms selected candidates.
- Require explicit “create another record” confirmation when a potential duplicate is selected.
- Apply the same rules for birthday, birth-year range, phone, email, language, facts, notes and timezone as manual entry.
- A partial import reports inserted, skipped, duplicate-reviewed and failed counts separately.
- Each row has a stable import ID/idempotency key across retry.
- Cancel from preview performs no writes.

### 6.2 Android device contacts

**FACT:** Device import explains the purpose of permission and asks before requesting READ_CONTACTS. It handles denial, reads name/phone/birthday through an Android method channel, converts rows to candidates, detects duplicates and uses the preview UI. It recognizes yearless --MM-DD, YYYY-MM-DD and MM/DD/YYYY date forms. A missing or unparseable birthday does not prevent the candidate from being formed; this differs from CSV parsing.

**Approved target:** A device-contact candidate without a valid birthday may be presented for review, but it cannot be selected for persistence until a valid birthday is supplied. The exact invalid-row presentation remains open. The app must not create a no-birthday Person from this path.


**PROPOSED CONTRACT:** Permission is requested only after explanation; denial is a valid exit path and must not block manual entry. The app reads only fields needed for import, never writes back to the address book, and does not continuously sync contacts. Every imported row still passes the shared validator before persistence.

### 6.3 CSV export

**FACT:** Export includes 15 profile columns: name, birthday month/day/year, phone, email, relationship, closeness, language, tone, facts, notes, preferred delivery channel, timezone and auto-prepare. Formula-leading values are guarded for spreadsheet safety; phone values preserve the leading plus sign. People without birthdays export blank month/day fields.

**Limit:** The CSV omits Person ID, created/updated timestamps, version, deletedAt and autoSendPolicy. It is a portable profile export, not a lossless database backup or complete cloud backup.

**PROPOSED CONTRACT:** Call this a contact/profile export, identify included fields, and distinguish it from full backup. Document delimiter, quoting, newline, date and formula-safety behavior.

## 7. Persistence, consistency and side effects

**FACT:** Persons has a primary key on id, and only this table currently has both version and deletedAt. Two adapters have overlapping responsibility:
- DriftPeopleStore backs PersonService for form create/edit/soft-delete/restore.
- DriftPeopleRepository backs global People streams and direct CSV/device import writes.

They map the same table but expose different query semantics: PeopleStore.getById can return a tombstoned row, while the app-level repository getPerson excludes tombstones. The repository boundary is split across two interfaces.

**PROPOSED CONTRACT:**
- Define one canonical persistence interface/adapter for Person and one owner for tombstone/version mutations.
- Define explicit active vs include-deleted query methods.
- Route all writes through one application service or aggregate boundary.
- Persist Person + Birthday in one transaction when the user action promises both.
- Trigger reminder/SMS reconciliation after commit only. Reconciliation failure must not roll back valid data, but must be observable and retryable.
- Use UTC timestamps consistently and inject the clock into lifecycle operations.

## 8. Required interaction states

| State | Expected UI behavior |
|---|---|
| Initial/loading | Stable loading state; no false empty flash |
| Empty | Explain manual add and import |
| Search no-match | Query-specific empty state and clear-search action |
| Create/edit pristine | No discard prompt if untouched |
| Create/edit dirty | Cancel/back asks to keep editing or discard |
| Validation failure | Highlight fields and preserve input |
| Saving | Disable duplicate submissions; show progress; preserve input on failure |
| Import parsing | Clear parser result and invalid-row feedback |
| Import preview | Row details, duplicate evidence, selected count and cancel |
| Permission denied | Explain device import unavailable; keep manual/CSV alternatives |
| Export no records | Say nothing was exported; do not open an empty share sheet |
| Delete confirmation | State linked-data consequences |
| Delete success | Offer Undo only if aggregate restoration is available |
| Delete/Undo partial failure | State what happened and offer recovery; never swallow silently |

## 9. Privacy and security

- Person fields are private personal data. Store locally by default.
- Contacts permission is not consent to upload data to cloud.
- Cloud backup is opt-in and outside this domain contract.
- Do not log names, phone numbers, email, dates, notes, facts or draft content.
- CSV export is user-initiated and may expose data through the Android share target; disclose this before export.
- User facts and notes are data, not executable prompt instructions.
- Do not assert E.164 normalization, encryption at rest, end-to-end encryption or permanent deletion without evidence.

## 10. Open product decisions

| ID | Question | Current source behavior | Owner |
|---|---|---|---|
| PPL-DEC-001 | Can a new Person exist without a birthday? | Device import can; manual form cannot | Product |
| PPL-DEC-002 | Should CSV round-trip no-birthday people? | Export can; parser rejects missing birthday | Product |
| PPL-DEC-003 | What is the canonical autoPrepare default? | Conflicting defaults across model, draft, form, candidate and SQL | Product |
| PPL-DEC-004 | Is phone normalization required and what country context applies? | Shape validation and digits-only duplicate comparison; no E.164 normalization | Product / Data |
| PPL-DEC-005 | Should editing allow clearing an existing birthday? | Current form does not permit it | Product |
| PPL-DEC-006 | Should import duplicates allow explicit second records or offer merge? | Candidate can be selected; no merge implementation | Product |
| PPL-DEC-007 | What is Undo retention period and aggregate scope? | Snackbar Undo exists; no durable retention contract | Product / Data |
| PPL-DEC-008 | Which repository abstraction is canonical? | Two adapters write the same table | Architecture / Data |
| PPL-DEC-009 | Is CSV profile export sufficient, or is full portable backup required? | Current CSV is not a full-fidelity backup | Product |

## 11. Source inventory

- lib/features/people/domain/models/person.dart — canonical Person model and JSON mapping.
- lib/features/people/domain/person_input.dart — PersonDraft and sync-envelope conversion.
- lib/features/people/domain/person_input_validator.dart — normal form validation.
- lib/features/people/application/person_service.dart — create/update/remove/restore and version ownership.
- lib/features/people/data/person_repository.dart — PeopleStore and DriftPeopleStore.
- lib/features/people/data/person_providers.dart — service/provider wiring and reminder reconciliation.
- lib/features/people/data/contact_csv_service.dart — CSV parser, candidate construction, duplicate detection and export.
- lib/features/people/data/device_contacts_service.dart — Android contacts method channel and date parsing.
- lib/features/people/presentation/person_form_screen.dart — add/edit interaction and Birthday write.
- lib/features/people/presentation/people_screen.dart — list, search, import, export, delete and Undo orchestration.
- lib/core/database/app_database.dart — Persons schema.
- lib/core/database/drift_repositories.dart — parallel app-level People repository.

Related: [Birthday lifecycle contract](birthday-lifecycle-contract.md) · [Acceptance criteria and traceability](acceptance-criteria-and-traceability.md).
