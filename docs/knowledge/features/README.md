# Functional taxonomy — AI-Birthday

[Knowledge portal](../README.md) · [Registry](../feature-registry.json) · [Journeys](../journeys/README.md) · [Architecture](../architecture/README.md)

## Recursively decomposed product tree

```text
AI-Birthday (APP)
├── First-run experience (ONB)
│   ├── Intro/walkthrough, skip, add/import actions (ONB-FLOW)
│   └── Completion flag and first-run redirect (ONB-GATE)
├── Navigation shell (NAV) — four persistent destinations + detail routes
├── Dashboard (DASH) — Today, Upcoming, Action Needed, quick actions
├── People (PEOPLE)
│   ├── Create/edit/soft delete/undo (PEOPLE-CRUD)
│   ├── Validate fields/duplicate checks (PEOPLE-VALIDATE)
│   ├── Import review (PEOPLE-IMPORT)
│   │   ├── CSV parse, normalization, warnings (PEOPLE-CSV)
│   │   └── Android contacts permission/import (PEOPLE-DEVICE)
│   └── CSV export/share (PEOPLE-EXPORT)
├── Birthdays (BIRTH)
│   ├── Recurrence, timezone, leap-day policy (BIRTH-CALC)
│   └── Occurrence creation / cycle rollover (BIRTH-CYCLE)
├── Calendar (CAL) — month paging, date/day sheet, Studio entry
├── Reminders (REM)
│   ├── Scheduling, offsets, quiet-hours suppression/defer (REM-RULES)
│   ├── Persisted switches, preferences, hiding names (REM-PREFS)
│   └── Native exact alarms / receiver / deep links (REM-NATIVE)
├── Message Studio (STUDIO)
│   ├── Editable text, draft/autosave (STUDIO-DRAFT)
│   ├── Generate with AI as durable queued job (STUDIO-AI)
│   └── Rewrite / translate / variations (STUDIO-EDIT)
├── AI (AI)
│   ├── Personal Gemini key + provider (AI-BYOK)
│   ├── Kotlin Gemini Nano integration (AI-NANO)
│   └── Prompt composition with user-supplied facts (AI-PROMPT)
├── Delivery (SEND)
│   ├── WhatsApp URL/handoff (SEND-WA)
│   ├── SMS intent (SEND-SMS)
│   ├── Native share/copy (SEND-SHARE)
│   └── Handoff evidence vs. user-confirmed sent (SEND-TRUTH)
├── History (HISTORY) — draft and delivery status timeline
├── Accounts (AUTH) — Google identity, Firebase token/session
├── Subscription (BILL) — Play Billing + server verification
├── Cloud (CLOUD)
│   ├── Backup snapshot generations (CLOUD-UP)
│   └── Validated restore with tombstone handling (CLOUD-DOWN)
├── Durable Jobs (JOBS) — queue, retry, startup recovery
├── Settings (SETTINGS) — AI, reminders, auth, purchases, backup
├── Design system (THEME) — app tokens, themes and shared widgets
├── Automatic preparation (AUTO-PREP) — preference present;
│   end-to-end scheduled generation still unverified
├── Separate public web site (WEB)
│   └── Deletion reauth/receipt (WEB-DELETE)
└── Separate coordination backend (CTRL; not integrated with Flutter sender flow)
    ├── Lease, claim, arm, retry fencing (CTRL-CLAIM)
    ├── Deletion and maintenance orchestration (CTRL-DELETE)
    └── Purchase verification (CTRL-PURCHASE)
```

The [machine-readable registry](../feature-registry.json) contains stable parent/child IDs, actual file paths, relevant tests, intent references and evidence limitations. The hierarchy stops at *meaningful* functional responsibilities; a validation condition or state transition may be a nano feature without requiring an individual Markdown file.

The current registry is the complete counted feature inventory for this snapshot. Its `parent` field defines hierarchy; source/test evidence and status remain on each node. A source node marked implemented does not mean deployed, production-ready, or runtime-verified. Detail nodes added in the current review cover search/filter, import diagnostics, recurrence/quiet hours, draft race guards, entitlement/provider selection, send confirmation, backup validation, job recovery, and known missing integration/release pieces.

## Deep decomposition: People → Import → CSV

| Level | Functional unit | Trigger / decision / output | Code |
|---|---|---|---|
| Domain | PEOPLE | Maintain a recipient's birthday context | [people_screen.dart](../../../lib/features/people/presentation/people_screen.dart) |
| Subfeature | PEOPLE-IMPORT | Choose import source, preview and select candidates | [people_screen.dart](../../../lib/features/people/presentation/people_screen.dart) |
| Micro | PEOPLE-CSV | Parse pasted CSV to structured candidates | [contact_csv_service.dart](../../../lib/features/people/data/contact_csv_service.dart) |
| Nano | CSV record tokenization | Handle delimiters and quoted fields | `_parseCsvLine` in contact service |
| Nano | Field/date normalization | Parse month/day/year, reject invalid dates | `parseCsvWithResult` |
| Nano | Duplicate hinting | Compare candidate fields to existing people | `parseCsvWithResult` |
| Nano | Row diagnostics | Report skipped/invalid CSV records | `CsvParseResult.invalidRows` |
| Atomic | Confirm selections | Save only user-selected valid candidates | `_reviewAndImportCandidates` in People screen |

Status: source-implemented; device/UI-runtime results not independently tested in this review.

## Deep decomposition: Studio → AI generation

| Level | Unit | Functional responsibility | Source |
|---|---|---|---|
| Feature | STUDIO | Edit a birthday greeting and choose delivery | [Studio](../../../lib/features/message_studio/presentation/message_studio_screen.dart) |
| Subfeature | STUDIO-AI | Generate an AI draft | `_generateWithAi` |
| Micro | Queue job | Persist request and resolve target draft/birthday | `_enqueueGenerate` |
| Nano | Route provider | Evaluate BYOK preference, entitlement, Nano availability | [AI router](../../../lib/features/ai/domain/ai_router.dart) |
| Nano | Bound execution | Apply provider timeout; return typed failure | `AiRouter._withTimeout` |
| Nano | Persist result | Save draft and update birthday lifecycle | [AI job handler](../../../lib/features/jobs/application/ai_job_handler.dart) |
| Nano | Recover failure | Surface retry/alternative/manual authoring | Studio job event handling |

**Approved policy distinction:** active application entitlement is required before either provider is selected or invoked. A personal Gemini API key changes the provider and whose API quota/billing applies; it does not bypass the app entitlement. With entitlement, a configured key is used unless Nano is explicitly selected; otherwise Nano is used only when genuinely available. Provider unavailability must preserve manual drafting. This is AI draft generation, not background message sending.

## Deep decomposition: Reminders → schedule

```text
REM
└── REM-RULES
    ├── Choose enabled reminder kinds (settings)
    ├── Derive upcoming occurrence (BirthdayEngine)
    ├── Calculate offset timestamp (ReminderScheduler)
    ├── Decide timezone and leap-day behavior
    ├── Apply quiet-hours policy
    │   ├── Suppress within closed hours (default)
    │   └── Defer if explicitly enabled
    ├── Produce active/suppressed/deferred plan
    └── Apply or cancel native alarms (ReminderService → Gateway)
```

Supporting files: [rules](../../../lib/features/reminders/domain/reminder_schedule.dart), [settings](../../../lib/features/reminders/application/reminder_settings_controller.dart), [service](../../../lib/features/reminders/application/reminder_service.dart).

## Deep decomposition: Backend coordination (separate subsystem)

```text
CTRL
├── Register Android installation and establish sender epoch
├── Renew sender lease and change account mode
├── Claim Birthday or test occurrence
├── Validate recipient/destination fences, quotas and deduplication
├── Arm a bounded send attempt and query arm status
├── Enforce retry conditions and cooldown
├── Initiate/complete sender transfer
├── Account deletion, redaction, receipt and drain
└── Scheduled recovery and cleanup
```

These are [callable Functions](../../../backend/functions/src/functions/index.ts) backed by [domain decisions](../../../backend/functions/src/domain/decisions.ts) and [control-plane orchestration](../../../backend/functions/src/services/controlPlane.ts). The separate backend's README says it is **not deployed**; this tree is not evidence of mobile app wiring.

## Feature contracts and status rules

Each nontrivial node in [the registry](../feature-registry.json) records: ID, parent, name, source location, known test, intended spec, implementation status, verification qualifier and caveat. A source implementation is *implemented in code*, not *working in production*. Runtime verification and deployment must be determined separately.

For each feature, test: entry/preconditions, successful output, invalid input, loading/errors, retries, permissions, state transitions, privacy implications, dependency behavior and navigation exit. See [journeys](../journeys/README.md) for cross-feature action paths and [gaps](../gaps/README.md) for known deficits.
