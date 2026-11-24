# Phase 2.4.1 — Decision register and option analysis

**Status vocabulary:**  
- **RECOMMENDED — TECHNICAL:** preferred architecture; engineering-owner sign-off required.  
- **RECOMMENDED — PRODUCT:** proposed user-facing default; product/privacy approval required.  
- **OPEN — EVIDENCE:** cannot be finalized until actual local data is inventoried.  
- **APPROVED:** only the four explicit Phase 2.3.1 decisions; no new approval is implied here.

## DEC-019 — Person/Birthday aggregate transaction boundary

**Recommended resolution — TECHNICAL:** Use one UnitOfWork backed by a single Drift/SQLite transaction for a Person mutation and all Birthday occurrence rows changed by that operation. Keep OS/network side effects outside SQLite; persist a reconciliation request in the same transaction and process it after commit.

**Alternatives and trade-offs:** Separate writes with compensating rollback are rejected because current flows already expose partial-failure paths. A DB transaction without durable post-commit reconciliation is simpler but can leave stale reminders after process death. A distributed transaction is unnecessary for this local aggregate.

**Impact:** Create/edit/restore and occurrence reconciliation share one transaction executor. Delete scope remains governed by DEC-027.

**Acceptance:** Fault injection after every write proves full rollback; no reminder/import/UI success is emitted before commit; post-commit failure is retryable.

**Status:** RECOMMENDED — TECHNICAL; Architecture/Data sign-off pending.

## DEC-020 and DEC-025 — Canonical Person repository and write ownership

**Recommended resolution — TECHNICAL:** Merge DEC-025 into DEC-020. Keep one PersonRepository port and one Drift implementation. All writes enter through one People application service. Providers, imports and forms use the same repository semantics, with explicit active and include-deleted operations.

**Alternatives and trade-offs:** Keeping two adapters with documented ownership preserves compatibility but continues split field mapping and tombstone behavior. A separate read/write repository pair is acceptable only if both delegate to a single mapper and transaction owner; otherwise it adds ambiguity. Direct widget/import writes are rejected.

**Impact:** Route callers to one port; compare field round-trips and tombstone behavior; migrate direct import writes; remove the old adapter only after a complete caller audit and parity tests.

**Acceptance:** Every field round-trips; active/deleted queries agree; version/tombstone behavior is identical; no direct Person table writers remain outside adapter/migration code.

**Status:** RECOMMENDED — TECHNICAL; Architecture/Data sign-off and cutover plan pending.

## DEC-022 — Prior-cycle draft and delivery-history retention

**Recommended resolution — PRODUCT:** Retain prior-cycle drafts and delivery evidence as read-only history linked to that exact cycle. Rollover must not rewrite the old occurrence identity or prune drafts only because the cycle is no longer current.

**Alternatives and trade-offs:** Deleting at rollover saves storage but destroys user-authored content. Keeping only the latest draft simplifies UI but weakens history. Indefinite retention preserves context but requires privacy and purge policy.

**Impact:** Depends on per-cycle identity under DEC-026. History UI, retention term and permanent purge after Person deletion must align with DEC-027. No retention duration is assumed.

**Acceptance:** Rollover creates a new occurrence ID; prior draft and delivery rows retain original IDs/status/timestamps; History shows the correct year; cleanup never deletes a linked cycle solely by age.

**Status:** RECOMMENDED — PRODUCT; Product/Privacy approval required.

## DEC-023 — Existing Person rows with missing/invalid birthday

**Recommended resolution — PRODUCT/DATA:** Preserve every existing row; mark it Needs birthday; do not fabricate a date or silently delete it. Keep viewing and export available. Do not create an occurrence/reminder. Require a valid birthday before a successful profile mutation that re-persists that Person.

**Alternatives and trade-offs:** Mass deletion is destructive. A placeholder date fabricates personal data and may trigger an incorrect message. Fully editable no-birthday rows conflict with the approved new-record contract. Hiding rows makes remediation difficult.

**Impact:** Legacy read models need a validation state. Export may preserve blank month/day with a warning. The affected record count and data shape are unknown until inventory.

**Acceptance:** Rows survive upgrade unchanged; no reminder is scheduled; row is visible as Needs birthday; correction creates a valid occurrence; no record is silently dropped.

**Status:** RECOMMENDED — PRODUCT/DATA; owner approval and aggregate-only inventory required.

## DEC-024 — Existing Feb 29 Persons without saved policy

**Recommended resolution — PRODUCT:** Prompt the user to choose Feb 28 or Mar 1 for each affected Person. Never infer intent from the previously stored Birthday date because that date may have been generated by the former default. Do not calculate a definitive non-leap occurrence or schedule its reminder before selection.

**Alternatives and trade-offs:** Mass-selecting Feb 28 or Mar 1 is easy but falsely represents implementation defaults as user preference. Leaving null and silently applying Feb 28 preserves the hidden default. A per-Person prompt preserves choice but creates migration work.

**Impact:** Add a nullable preference representation for legacy migration; require a value for new Feb 29 Persons. Prompt timing and UX require product approval.

**Acceptance:** Missing preference is detected; no silent default is used; selection persists; leap years remain Feb 29; reminders resume only after selection.

**Status:** RECOMMENDED — PRODUCT; Product approval and affected-record count required.

## DEC-026 — Birthday occurrence persistence model

**Recommended resolution — PRODUCT/DATA:** Use a durable Birthday occurrence identity per Person per cycle year. Enforce one record for a given (personId, cycleYear). Prior cycle ID/date/status is retained; a new year gets a new record. The row remains mutable for legitimate workflow transitions, but its cycle identity is never repurposed.

**Alternatives and trade-offs:** One mutable row per Person matches the current ID pattern but overwrites year/date and can misassociate old drafts. Fully append-only event sourcing over-models every workflow transition. A per-cycle record with mutable state preserves history with a conventional relational lifecycle.

**Impact:** Migrate the current Birthday row to its cycle-specific identity and update MessageDrafts/DeliveryEvents birthdayId references atomically. Unique key is composite. Current occurrence is determined from cycle/date and lifecycle state.

**Acceptance:** One row per Person/year; rollover creates rather than overwrites; old draft links remain unchanged; retries are idempotent; duplicate composite keys are rejected.

**Status:** RECOMMENDED — PRODUCT/DATA; owner approval required because History and schema change.

## DEC-027 — Delete and Undo aggregate scope

**Recommended resolution — PRODUCT/PRIVACY:** Treat Person as the visibility root. Delete tombstones Person and hides all linked cycle, draft and delivery-history records from active views; keep links intact while Undo is available. Undo restores the same aggregate and associations. Permanent purge, retention duration and treatment of user-confirmed delivery evidence need a separate privacy-approved policy.

**Alternatives and trade-offs:** Hard-deleting Birthday immediately leaves drafts orphaned. Immediate cascade deletion is not undoable and may destroy evidence without policy. Soft-deleting only Person while children remain visible is inconsistent. Soft-delete/hide then approved purge is preferred.

**Impact:** Active occurrence/draft/history queries must filter through active Person state. Reminder/job cancellation occurs after commit and is retryable. No Undo duration is invented.

**Acceptance:** Delete hides whole aggregate; Undo restores exact links/states; process death does not revive reminders; permanent purge follows approved retention policy.

**Status:** RECOMMENDED — PRODUCT/PRIVACY; approval required; do not implement purge until policy is approved.

## DEC-028 — Phone normalization and country context

**Recommended resolution — PRODUCT/UX:** Parse international numbers with maintained numbering metadata. For local-format numbers require explicit country selection. Store canonical E.164 for delivery and duplicate comparison; retain formatted display separately if needed. Never infer country from device location or IP.

**Alternatives and trade-offs:** Guessing a country is unsafe. Raw text forever preserves input but yields inconsistent delivery and duplicate matching. Forcing every user to type the country prefix is valid but poor local UX. Explicit country selection is preferred.

**Impact:** Requires a vetted parsing dependency, country selector and migration review. Legacy values are not auto-rewritten.

**Acceptance:** Local number without country is blocked until selection; explicit international number validates; E.164 is stored; ambiguous number is not guessed; duplicate comparison uses canonical values only.

**Status:** RECOMMENDED — PRODUCT/UX; approval and dependency/security review required.

## DEC-029 — Invalid recipient timezone

**Recommended resolution — PRODUCT/ANDROID:** Distinguish absent from invalid. Absent uses the approved device-local fallback. Invalid non-empty values are surfaced as invalid data; do not silently rewrite or treat them as absent. Show correction action and pause recipient-specific reminder scheduling until repaired.

**Alternatives and trade-offs:** Silent device fallback avoids crashes but may change the recipient's intended calendar date. Throwing through a stream is unacceptable. Auto-deleting the invalid value loses data without consent. Preserving it with visible warning is safer.

**Impact:** Resolver returns typed valid/missing/invalid/device-unavailable states. Device timezone discovery failure needs a separate recovery rule.

**Acceptance:** Invalid timezone cannot crash stream; original value is preserved; no false exact countdown/reminder is shown; correction restores calculation and scheduling.

**Status:** RECOMMENDED — PRODUCT/ANDROID; approval required.

## DEC-030 — Invalid birthday import candidate UX

**Recommended resolution — PRODUCT/UX:** Show each raw candidate in preview with a field-level error, unselected and disabled for persistence until a valid birthday is supplied. Preserve row-level source diagnostics and allow a deliberate correction step.

**Alternatives and trade-offs:** Silently dropping rows loses information. Letting invalid rows be selected and failing at save increases retry cost. Rejecting an entire mixed-quality file is overly strict. Visible invalid candidates with correction is preferred.

**Impact:** Candidate model must represent validation errors; selection cannot override validation; summary counts invalid and created rows separately.

**Acceptance:** Invalid rows are visible and cannot be committed; correction makes row eligible; cancel creates no data; mixed valid/invalid imports report accurate counts.

**Status:** RECOMMENDED — PRODUCT/UX; approval required.

## DEC-031 — Existing autoPrepare values

**Recommended resolution — PRODUCT/DATA:** Preserve stored booleans exactly during migration, including true. Apply false only to new records and values genuinely absent/defaulted. Do not mass-reset because the current schema does not show whether true came from user choice or inconsistent constructor/import defaults.

**Alternatives and trade-offs:** Resetting all to false may erase genuine preferences. Preserving true may retain some accidental old defaults. Prompting every user is high-friction and provenance may be unknowable. Preservation is the safest reversible migration default.

**Impact:** New constructors/forms/import defaults become false. Existing records are unchanged unless the user edits the preference. Provenance-based cleanup is deferred unless reliable evidence exists.

**Acceptance:** New records default false; existing true and false values survive; explicit edit persists; no migration-wide UPDATE changes preferences.

**Status:** RECOMMENDED — PRODUCT/DATA; approval required.

## DEC-032 — Database constraints and repair ordering

**Recommended resolution — TECHNICAL:** After approved migrations and a zero-violation preflight, enforce Person birthday month/day NOT NULL; Birthday UNIQUE(personId, cycleYear); Birthday.personId foreign key to Persons.id with ON DELETE RESTRICT; application-managed soft deletion rather than SQL cascade.

**Alternatives and trade-offs:** Unique personId alone prevents per-cycle history. No unique constraint allows races to create duplicates. ON DELETE CASCADE bypasses retention/Undo orchestration. Composite uniqueness plus RESTRICT matches the proposed cycle identity and makes destructive deletion explicit.

**Impact:** Inventory and repair partial birthdays, duplicate/orphan cycle rows and linked records first. Add NOT NULL only after DEC-023 and remaining exceptions are resolved or represented by an approved compatibility model.

**Acceptance:** Preflight reports zero violations; duplicate composite insert fails; orphan insert fails; Person hard delete is blocked while child rows exist; supported v5 upgrades pass.

**Status:** RECOMMENDED — TECHNICAL; Architecture/Data sign-off and inventory required.

## DEC-033 — Refresh across recipient timezones

**Recommended resolution — TECHNICAL:** Use event-driven reconciliation plus one in-process timer for the earliest upcoming midnight across distinct active recipient timezones, including device-local context for People without a recipient timezone. Recompute after app resume, device timezone changes and each completed reconciliation. Reminder alarms remain separately based on canonical occurrence and user offsets.

**Alternatives and trade-offs:** One device-local timer misses other recipient date boundaries. One timer per Person duplicates work for shared timezones. Minute polling wastes resources. One timer for the earliest boundary across distinct zones plus lifecycle reconciliation is preferred.

**Impact:** Requires an IANA-zone-aware next-midnight calculation and scheduler abstraction. Background reminders must not depend on an in-process timer while the app is killed.

**Acceptance:** Each recipient zone refreshes at its own date boundary; people sharing a zone share one boundary; app resume catches missed boundaries; device timezone changes recalculate; reminders remain occurrence-based.

**Status:** RECOMMENDED — TECHNICAL; Android/Architecture sign-off and device validation required.

## DEC-034 — Birthday occurrence durability and sync

**Recommended resolution — DATA/SYNC:** Treat each per-cycle Birthday record as durable local workflow state because it contains status and draft linkage, not merely a computable date. The resolver may recompute date/cycle facts, but status and associations survive restarts. Do not independently synchronize occurrence rows until DEC-015 defines snapshot consistency and restore behavior.

**Alternatives and trade-offs:** A disposable derived cache loses workflow state. Independent sync is premature while backup versus bidirectional sync remains open. Durable local per-cycle records with sync deferred is preferred.

**Impact:** No version/tombstone fields are required solely for local persistence. If cloud backup later includes these rows, version/conflict rules are part of that approved contract.

**Acceptance:** Status and draft associations survive restart; date reconciliation does not reset same-cycle workflow state; restore behavior is tested when DEC-015 is decided.

**Status:** RECOMMENDED — DATA/SYNC; approval required.

## DEC-035 — Legacy edit and export behavior

**Recommended resolution — PRODUCT/DATA:** Keep legacy no-birthday rows readable and visible as Needs birthday. Preserve blank month/day in profile CSV export and show an export warning/count. On import, report the row as invalid until corrected. Do not omit, populate or rewrite its birthday. Require valid birthday before a successful profile mutation.

**Alternatives and trade-offs:** Excluding rows hides user data. Placeholder dates fabricate sensitive data. Exporting blanks without warning creates a confusing round-trip failure. Preserving blanks with a clear warning is preferred.

**Impact:** Export summary and import preview need row-level diagnostics. Read compatibility remains until remediation completes.

**Acceptance:** Legacy records remain exportable; export warns; re-import reports validation error; correction makes row persistable; no invented date is stored.

**Status:** RECOMMENDED — PRODUCT/DATA; approval required.
