# 03 — Requirements baseline

**Status:** Initial requirement set; detailed feature contracts and stakeholder acceptance remain in progress.

## 1. Requirement conventions

IDs are stable and must never be reused:
- `PRD-###`: product outcome / requirement.
- `FR-###`: functional requirement.
- `NFR-###`: non-functional requirement.
- `BR-###`: business rule (see [05](05-business-rules.md)).
- `JRN-###`: user journey (see [04](04-experience-and-journeys.md)).
- `SEC-###`: security/privacy requirement (see [07](07-security-and-privacy.md)).
- `TST-###`: test obligation (see [08](08-quality-and-verification.md)).

Priority: `P0` required for safe core use; `P1` important supporting capability; `P2` conditional or later. Status: `proposed`, `approved`, `implemented-evidence`, `verified`, `blocked`, `retired`. A source implementation does not change a requirement to `verified`.

## 2. Product requirements

| ID | Requirement | Priority | Status | Acceptance outcome |
|---|---|---:|---|---|
| PRD-001 | A user can maintain a trusted set of people and birthday facts | P0 | proposed | Saved records can be reviewed, edited, exported and deleted |
| PRD-002 | The app identifies upcoming birthday occurrences correctly | P0 | proposed | Date engine produces deterministic next occurrences under approved calendar rules |
| PRD-003 | The user can understand what needs attention today and soon | P0 | proposed | Dashboard and calendar agree on occurrence data |
| PRD-004 | The user can prepare a greeting manually | P0 | proposed | AI is not required for a complete draft |
| PRD-005 | The user can optionally use subscription-entitled AI | P0 | proposed | No provider is called before entitlement succeeds |
| PRD-006 | The user retains final control of message content and sending | P0 | proposed | External handoff requires explicit user action and review |
| PRD-007 | The app records truthful delivery lifecycle evidence | P0 | proposed | Handoff is never reported as confirmed send |
| PRD-008 | The core local workflow remains useful offline | P0 | proposed | People, birthday, calendar and manual draft paths work without cloud |
| PRD-009 | Optional cloud features preserve account ownership and data integrity | P1 | proposed | Backup/restore is opt-in, validated and recoverable |
| PRD-010 | Users can control reminders and privacy preferences | P0 | proposed | Preferences persist and OS permission states are explained |
| PRD-011 | Users can understand subscription and AI availability | P0 | proposed | Entitlement, provider readiness and error reasons are distinct |
| PRD-012 | Users can manage account and personal data lifecycle | P1 | proposed | Sign-out, export, deletion and backup effects are explicit |
| PRD-013 | The app supports accessible, responsive use | P0 | proposed | No critical journey fails at supported text scaling and screen widths |
| PRD-014 | Operational claims match deployed and tested evidence | P0 | proposed | Release checklist blocks unverified production claims |

## 3. Functional requirements

### People and relationships
| ID | Requirement | Priority | Acceptance criteria |
|---|---|---:|---|
| FR-001 | Create a person with a required display name and valid month/day; optional birth year and personalization context | P0 | Invalid values are rejected with field-level explanations; save failure preserves input |
| FR-002 | Edit a person without silently changing unrelated enum, preference, consent or sync fields | P0 | Round-trip tests preserve every field and unknown legacy values are handled explicitly |
| FR-003 | Soft-delete a person with a clear undo window and consistent occurrence/draft cleanup | P0 | Undo restores all required linked data; deletion is propagated correctly to backup |
| FR-004 | Import CSV and device contacts through a preview-and-select flow | P1 | Permission denial, malformed rows and duplicates are reported before writes |
| FR-005 | Export user-owned people data in a documented portable format | P1 | Export is user initiated, complete for declared fields and excludes secrets |

### Birthday lifecycle and reminders
| ID | Requirement | Priority | Acceptance criteria |
|---|---|---:|---|
| FR-006 | Resolve the next birthday occurrence using one centralized calendar policy | P0 | Leap-day, year boundary, timezone and daylight-saving test vectors pass |
| FR-007 | Maintain one canonical occurrence identity per person and cycle | P0 | Repeated refresh is idempotent and cannot create duplicate occurrences |
| FR-008 | Transition occurrence status only through documented legal transitions | P0 | Invalid transitions are rejected or mapped through an explicit recovery path |
| FR-009 | Schedule configured reminder offsets and quiet hours | P0 | Recompute/cancel is deterministic after preference, permission, timezone and reboot changes |
| FR-010 | Show consistent date information across dashboard, calendar, reminder and history | P0 | All views derive from the same occurrence service |

### Message preparation and AI
| ID | Requirement | Priority | Acceptance criteria |
|---|---|---:|---|
| FR-011 | Create, edit, autosave and reopen manual drafts | P0 | Latest committed revision is restored after navigation and app restart |
| FR-012 | Require active application entitlement before any AI provider is selected or called | P0 | BYOK and Nano both return locked without entitlement; no provider network/native call occurs |
| FR-013 | Route entitled AI requests to a valid user Gemini credential or supported Nano provider | P1 | Provider choice and failure reason are observable without exposing secrets |
| FR-014 | Build prompts only from recipient facts explicitly supplied or approved by the user | P0 | Prompt tests prove no invented or cross-recipient context |
| FR-015 | Support generation, regenerate, rewrite, shorten, expand, tone and translation only where provider capability is available | P1 | Each operation preserves recipient and draft identity and records resulting revision |
| FR-016 | Handle invalid credentials, quota, provider unavailable, timeout, cancellation and stale response | P0 | Typed errors preserve the last valid draft and provide safe next actions |

### Delivery and history
| ID | Requirement | Priority | Acceptance criteria |
|---|---|---:|---|
| FR-017 | Build a validated WhatsApp Click-to-Chat handoff | P0 | International number and encoded text are correct; launch does not mark sent |
| FR-018 | Provide SMS composer, copy and share fallback paths | P1 | Each path records the actual handoff action and channel |
| FR-019 | Require explicit user confirmation before marking a message user-confirmed sent | P0 | No background or launcher callback can create a sent confirmation |
| FR-020 | Maintain immutable or append-only delivery event evidence | P1 | History distinguishes draft, handoff, user confirmation and unknown external outcome |
| FR-021 | Offer personal-flavor automated SMS only under separately approved consent and release policy | P2 | Disabled by default; per-recipient approval, edit revocation, stale expiry and flavor gating are tested |

### Account, subscription, backup and operations
| ID | Requirement | Priority | Acceptance criteria |
|---|---|---:|---|
| FR-022 | Authenticate users only through supported real identity flows | P1 | No synthetic OTP, fake user or local entitlement bypass |
| FR-023 | Verify purchases server-side before granting entitlement | P0 for paid AI | Invalid, expired, revoked, mismatched and replayed purchases do not unlock AI |
| FR-024 | Keep backup opt-in and owner-scoped | P1 | No data upload before explicit opt-in; cross-account access is denied |
| FR-025 | Validate a complete restore set before mutating local state | P1 | Partial/corrupt backup cannot leave a mixed or silently corrupted database |
| FR-026 | Run durable jobs idempotently with cancellation and bounded retry | P1 | Duplicate job delivery cannot duplicate user-visible side effects |
| FR-027 | Provide account deletion with clear scope, status and verified completion | P1 | Client reports accepted vs completed distinctly; deletion is auditable |
| FR-028 | Keep separate control-plane operations isolated from core mobile messaging | Conditional | No mobile workflow assumes control-plane deployment or capability without verified integration |

## 4. Non-functional requirements

Numeric service levels are intentionally not invented. Targets must be approved after baseline measurement.

| ID | Quality attribute | Requirement |
|---|---|---|
| NFR-001 | Reliability | Core local data operations are transactional or recoverable; failures do not silently discard user input |
| NFR-002 | Offline | People, birthdays, calendar and manual drafting do not require network access |
| NFR-003 | Performance | Common local list, dashboard and calendar interactions meet an approved responsiveness budget on supported baseline devices |
| NFR-004 | Privacy | Sensitive recipient facts and message bodies are not logged or sent to analytics by default |
| NFR-005 | Security | Secrets use platform secure storage; authorization is enforced server-side for cloud resources |
| NFR-006 | Accessibility | Screen reader labels, logical focus, contrast, touch targets and large text are tested |
| NFR-007 | Compatibility | Android versions/devices are explicitly declared and validated; platform-specific capabilities fail gracefully |
| NFR-008 | Maintainability | Each domain has a clear owner, contract, tests and documented state transitions |
| NFR-009 | Observability | Diagnostics use redacted, structured events and never require message-body collection |
| NFR-010 | Recoverability | Backup, restore, deletion, migration and rollback have rehearsed procedures |
| NFR-011 | Interoperability | Exported user data uses documented formats and stable field semantics |
| NFR-012 | Compliance | Store, privacy, messaging and data-deletion requirements are checked for each distribution channel |

## 5. Feature contract template

Every atomic feature specification must complete this template before it is marked implementation-ready:

1. Feature ID, name, domain owner and status.
2. Purpose, user problem and expected outcome.
3. Actor, role and permission matrix.
4. Preconditions and postconditions.
5. Inputs, outputs and canonical data owner.
6. Main flow with ordered user and system actions.
7. Alternate, empty, loading, success and failure states.
8. Field validation and normalization.
9. Business rules and legal state transitions.
10. Search, sort and filter behavior where applicable.
11. Notification and communication behavior where applicable.
12. API, persistence and external dependency contracts.
13. Security, privacy, consent and retention implications.
14. Idempotency, concurrency, retry and cancellation semantics.
15. Edge cases and recovery behavior.
16. Accessibility and responsive UI requirements.
17. Acceptance criteria in Given/When/Then form.
18. Unit, integration, E2E, security and regression tests.
19. Dependencies, priority and release phase.
20. Source evidence and implementation verification status.

## 6. Scoped domain contract references

Phase 2.3 provides source-backed details and named test mappings for the People and Birthday foundations:

- [People lifecycle contract](phase-2.3-product-specification/people-lifecycle-contract.md) expands FR-001–FR-005, including create/edit/delete/undo, imports, export, validation, data ownership and unresolved defaults.
- [Birthday lifecycle contract](phase-2.3-product-specification/birthday-lifecycle-contract.md) expands FR-006–FR-008 and FR-010, including Gregorian recurrence, leap-day resolution, timezone semantics, active occurrence refresh and aggregate integrity.
- [Acceptance criteria and traceability](phase-2.3-product-specification/acceptance-criteria-and-traceability.md) maps scoped requirements to source files, named existing tests and explicit gaps.

These references do not approve open decisions or close feature-contract gaps in other domains.

## 7. Requirements traceability

Each FR must link to at least one domain specification, journey, business rule, acceptance criterion and test case. An FR without a test mapping is not ready for implementation. A test without a requirement or defect reference must explain why it exists.
