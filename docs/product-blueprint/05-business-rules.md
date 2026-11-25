# 05 — Business rules and lifecycle semantics

**Rule status:** proposed baseline. Rules that conflict with current source must be reconciled and approved before implementation changes.

## 1. Canonical business rules

| ID | Rule | Owner | Priority | Dependencies | Acceptance evidence |
|---|---|---|---:|---|---|
| BR-001 | Person name is required; trim and validate length using one canonical validator | People | P0 | FR-001 | Valid/invalid boundary tests |
| BR-002 | Every newly persisted Person must have a valid Gregorian birthday month/day; birth year is optional | Birthday / People | P0 | FR-001, FR-006 | Manual, CSV and device-intake validation tests; legacy null rows handled separately |
| BR-003 | For a Feb 29 Person, the user selects Feb 28 or Mar 1 for non-leap-year occurrence; leap years use Feb 29 | Product/Birthday | P0 | BR-002 | Persisted per-Person choice, onboarding/edit validation and table-driven recurrence tests |
| BR-004 | Birthday cycle identity must be deterministic and unique per person/year under the approved timezone policy | Birthday | P0 | BR-002 | Idempotent refresh tests |
| BR-005 | Person edit must not silently mutate unrelated relationship, delivery, consent, tombstone or sync fields | People/Data | P0 | FR-002 | Full round-trip tests |
| BR-006 | Delete/undo must restore the complete declared aggregate or explicitly disclose what cannot be restored | People/Data | P0 | FR-003 | Person + birthday + draft lifecycle tests |
| BR-007 | Reminder plans derive from canonical occurrence data and persisted preferences | Reminders | P0 | FR-009 | Scheduler deterministic tests |
| BR-008 | Quiet hours must be evaluated across midnight and timezone changes under one documented policy | Reminders | P0 | BR-007 | Boundary and timezone tests |
| BR-009 | AI is not available unless the application subscription entitlement is active | Entitlement/AI | P0 | FR-012, FR-023 | All-provider locked tests |
| BR-010 | Entitlement validation must complete before provider selection or invocation | AI | P0 | BR-009 | Assert no provider call when locked |
| BR-011 | BYOK does not bypass application entitlement; a user key selects the provider only after entitlement passes | AI | P0 | BR-009, BR-010 | BYOK with no entitlement remains locked |
| BR-012 | Gemini Nano also requires active application entitlement | AI | P0 | BR-009, BR-010 | Nano with no entitlement remains locked |
| BR-013 | A configured but invalid API key is an error, not an absent key or implicit fallback | AI | P0 | FR-013, FR-016 | Invalid credential tests |
| BR-014 | Provider unavailable or AI failure never blocks manual message composition | Message Studio | P0 | FR-004, FR-016 | Manual path with AI disabled |
| BR-015 | AI may use only recipient facts the user supplied or explicitly approved; it must not invent personal details | AI/Privacy | P0 | FR-014 | Prompt boundary tests |
| BR-016 | AI output is always an editable draft; it cannot directly send or confirm a message | AI/Delivery | P0 | FR-019 | No provider-to-send call path |
| BR-017 | User must review content before external handoff | Message Studio | P0 | FR-017 | Handoff blocked for empty/unreviewed content per approved UX |
| BR-018 | Opening WhatsApp or SMS composer is a handoff event, not proof of send or delivery | Delivery | P0 | FR-019 | State transition tests |
| BR-019 | Only explicit user confirmation may mark a manual handoff as user-confirmed sent | Delivery | P0 | FR-019 | Confirmation-only writer test |
| BR-020 | Unknown outcome remains unknown; no timeout or app resume may auto-mark sent | Delivery | P0 | BR-018, BR-019 | Lifecycle/restart tests |
| BR-021 | Automatic SMS, if retained, is isolated to the personal distribution flavor and requires explicit recipient-level approval | Delivery | P2 | FR-021 | Flavor manifest and consent tests |
| BR-022 | Editing an automatically approved SMS draft revokes approval and cancels pending work | Delivery | P2 | BR-021 | Revision invalidation tests |
| BR-023 | SMS automation must have stale expiry, bounded retries and duplicate-send protection | Delivery | P2 | BR-021 | Race, expiry and retry tests |
| BR-024 | Cloud backup is off until explicit user opt-in | Data continuity | P1 | FR-024 | No network write before opt-in |
| BR-025 | Backup records are owner-scoped and must never include API credentials or auth secrets | Data continuity | P0 | FR-024 | Rules + serialization tests |
| BR-026 | Backup must not be described as end-to-end encrypted unless encryption is implemented and independently verified | Privacy | P0 | FR-024 | Documentation/security review |
| BR-027 | Restore validates the complete manifest and records before mutating local data | Data continuity | P1 | FR-025 | Corrupt/partial restore tests |
| BR-028 | Local/cloud conflict resolution must never silently replace newer user edits with older snapshots | Data continuity | P0 | FR-025 | Conflict matrix and property tests |
| BR-029 | Purchase entitlement is granted only from trusted server verification, not client flags or token presence | Subscription | P0 | FR-023 | Purchase verification negative tests |
| BR-030 | Sign-out and account deletion are different operations; local data retention/cleanup must be explained separately | Account | P1 | FR-027 | Journey and deletion tests |
| BR-031 | Account deletion request acceptance is not deletion completion | Account/Backend | P0 | FR-027 | Receipt-state tests |
| BR-032 | Birthday Autopilot is an optional in-scope Android capability; its control plane must remain isolated, and integration/activation requires explicit safety, policy and deployment approval without blocking manual workflows | Platform | P0 | FR-028 | Endpoint caller matrix, offline/manual fallback, policy and deployment evidence |
| BR-033 | A job must be idempotent, bounded in retries and cancellable where its side effect has not started | Jobs | P1 | FR-026 | Duplicate delivery and cancellation tests |
| BR-034 | Notification permission denial must not block manual access to birthday information | Reminders | P0 | FR-009 | Permission-denied journey |
| BR-035 | Product status labels must reflect canonical state and cannot be inferred from UI navigation alone | Experience | P0 | FR-020 | Cross-screen status consistency tests |
| BR-036 | New Persons default autoPrepare to false across model, form, database and all import paths; only explicit user choice enables it | People / Product | P0 | FR-001, FR-004 | Cross-path default tests and explicit-opt-in preservation |
| BR-037 | Birthday calculations use a valid recipient IANA timezone when present; when absent, they use device-local time | Birthday / Android | P0 | FR-006, FR-010 | Recipient-zone, missing-zone, midnight, DST, app-resume and device-timezone-change tests |


## 2. State models

### Birthday occurrence (proposed canonical model)

```text
UPCOMING
  ├──> REMINDER_DUE
  ├──> PREPARATION_NEEDED
  └──> EXPIRED / ROLLED_OVER (system-controlled by cycle policy)

PREPARATION_NEEDED
  └──> DRAFTED
DRAFTED
  └──> READY_FOR_REVIEW
READY_FOR_REVIEW
  └──> HANDED_OFF
HANDED_OFF
  ├──> USER_CONFIRMED_SENT
  ├──> NOT_SENT (user-declared)
  └──> OUTCOME_UNKNOWN
USER_CONFIRMED_SENT
  └──> COMPLETED
```

Failure is not one universal terminal status. Store failure category and retryability separately from lifecycle state. The exact mapping to existing enums must be done after source and data migration review.

### Draft lifecycle (proposed)

```text
EMPTY → EDITING → SAVED → READY_FOR_REVIEW
                         └──> SUPERSEDED (new revision)
```

Draft content revision and delivery event history must be separate. A handoff must not mutate the draft to a misleading "sent" state.

### AI request lifecycle (proposed)

```text
CREATED → ENTITLEMENT_CHECKED → PROVIDER_RESOLVED → QUEUED → RUNNING
   ├──> LOCKED
   ├──> UNAVAILABLE
   ├──> CANCELLED
   └──> FAILED
RUNNING → SUCCEEDED | FAILED | TIMED_OUT | CANCELLED | STALE_RESULT
```

### Delivery event lifecycle (proposed)

```text
PREPARED → HANDOFF_REQUESTED → HANDED_OFF
                                ├──> USER_CONFIRMED_SENT
                                ├──> USER_CONFIRMED_NOT_SENT
                                └──> OUTCOME_UNKNOWN
```

## 3. Rule governance

Separate product approval from implementation readiness:

- **Product intent approved:** the product owner explicitly confirms the desired user-facing behavior. This status can be recorded before code, schema or tests are changed.
- **Implementation-ready:** domain owners confirm feasibility and data ownership; security/privacy review is complete where applicable; legacy conflicts and migration behavior are resolved; acceptance criteria and tests are specified with owners.
- **Implemented/verified:** code, migration and tests exist and evidence is tied to an exact commit and environment.

A governance row may therefore say “approved product rule; implementation pending.” That does not mean source behavior already conforms or that implementation is authorized.

Source comments, enums and old documentation are not sufficient approval.
