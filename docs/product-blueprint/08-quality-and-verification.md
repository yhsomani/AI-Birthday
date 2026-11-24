# 08 — Quality and verification strategy

## 1. Evidence hierarchy

1. Reproducible production/device observation with timestamp and environment.
2. Passing CI run tied to an exact commit and workflow.
3. Passing automated test tied to an exact assertion and code revision.
4. Static source inspection.
5. Documentation claim.
6. Historical status label.

Never promote a lower evidence class into a higher one.

## 2. Test layers

| Layer | Scope | Required examples |
|---|---|---|
| Unit/domain | Date, recurrence, validation, lifecycle, state transitions, prompt policy | Leap day, year boundary, timezone, invalid enum, illegal transition |
| Repository/database | CRUD, migrations, transactions, tombstones, restore | Full field round-trip, delete/undo, orphan prevention, old schema upgrade |
| Widget/component | Forms, empty/error/loading states, responsive behavior | Keyboard, large text, denied permissions, save failure |
| API/backend | Auth, entitlement, backup, deletion, control plane | Invalid token, wrong UID, App Check, replay, malformed request |
| Platform integration | Android alarms, notifications, contacts, Nano, SMS | Reboot, permission revoke, unsupported Nano, flavor manifest |
| End-to-end | Complete user journeys | First launch → person → reminder → draft → handoff → confirmation |
| Security | Threat scenarios and authorization | Secret leakage, cross-user access, entitlement bypass, prompt injection |
| Performance | Startup, dashboard, calendar, database, queue | Approved device/data-volume benchmarks |
| Accessibility | TalkBack, text scaling, contrast, focus, reduced motion | 1.5x/2x text where supported, keyboard-only and screen-reader paths |
| Release verification | Signing, config, rollout, rollback | Correct flavor, provenance, staged rollout, smoke test |

## 3. Critical test scenarios

### Birthday correctness
- Today, tomorrow, yesterday and year boundary.
- February 29 in leap and non-leap years under approved policy.
- Birth year absent/present and milestone age.
- Device timezone changed before/after scheduled reminder.
- Daylight-saving transitions where applicable.
- Repeated lifecycle refresh and duplicate occurrence prevention.

### Data integrity
- Create/edit/delete/undo across person, occurrence, draft and reminder records.
- All fields survive serialization and deserialization.
- Legacy enum values do not silently coerce to unrelated values.
- Failed transaction leaves aggregate consistent.
- Cloud restore with missing, duplicate, malformed, stale and newer records.
- Tombstone propagation and resurrection prevention.
- Concurrent local edit during upload/restore.

### AI and entitlement
- No entitlement + BYOK configured → AI locked; provider call count is zero.
- No entitlement + Nano available → AI locked; native call count is zero.
- Active entitlement + valid key → Gemini API path.
- Active entitlement + no key + supported Nano → Nano path.
- Active entitlement + no available provider → unavailable; manual draft remains.
- Invalid configured key → credential error, not fallback as if key were absent.
- Timeout/quota/cancel/stale result → last valid draft preserved.
- Prompt injection in names/notes/facts cannot override system policy or create factual claims.

### Delivery truthfulness
- WhatsApp URI encoding and international number normalization.
- External app missing or launch rejected.
- Launch accepted but user does not send.
- User confirms not sent.
- User confirms sent.
- App killed between handoff and confirmation.
- Retry does not duplicate or fabricate a prior event.
- History and dashboard agree on canonical delivery event.

### Notifications and native behavior
- Notification permission denied, later granted and later revoked.
- Exact alarm permission unavailable/revoked.
- Device reboot and app update.
- Quiet hours crossing midnight.
- App timezone and recipient timezone behavior.
- Duplicate alarm cancellation and rescheduling.
- Notification tap opens the intended person/occurrence.

### Subscription and cloud
- Purchase pending, cancelled, expired, revoked, invalid, replayed and account-mismatched.
- Server unavailable before purchase and after purchase.
- Restore purchase and account switch.
- Backup disabled, upload interrupted, restore corrupt, user signed out.
- Account deletion request accepted vs deletion complete.
- Control-plane concurrency, replay, rate limits and deletion fences.

## 4. Traceability contract

| Requirement | Journey | Business rule | Acceptance/test group |
|---|---|---|---|
| FR-001 to FR-005 | JRN-002 | BR-001, BR-005, BR-006 | TST-PEOPLE |
| FR-006 to FR-010 | JRN-003 | BR-002 to BR-008, BR-034 | TST-BIRTHDAY, TST-REMINDER |
| FR-011 to FR-016 | JRN-004, JRN-005 | BR-009 to BR-016 | TST-DRAFT, TST-AI |
| FR-017 to FR-021 | JRN-006 | BR-017 to BR-023 | TST-DELIVERY |
| FR-022 to FR-023 | JRN-008 | BR-029 | TST-AUTH, TST-BILLING |
| FR-024 to FR-025 | JRN-007 | BR-024 to BR-028 | TST-BACKUP |
| FR-026 | JRN-005, JRN-007 | BR-033 | TST-JOBS |
| FR-027 to FR-028 | JRN-009 | BR-030 to BR-032 | TST-DELETION, TST-CONTROL |

Detailed test case IDs and source/test links must be expanded during the source verification pass. The group labels above are taxonomy, not evidence that the tests currently exist or pass.

## 5. Quality gates

### Gate A — Specification readiness
- Every P0 requirement has an owner and acceptance criteria.
- Business rules are approved and non-contradictory.
- Data ownership and external contracts are defined.
- Security/privacy impact assessed.
- Dependencies and rollback strategy documented.

### Gate B — Feature completion
- Unit, repository and component tests pass.
- Negative, boundary and failure cases pass.
- Accessibility and responsive checks pass.
- No unresolved critical/high security defect.
- Logs and error states are truthful.
- Documentation and traceability updated.

### Gate C — Release readiness
- CI green on exact release commit.
- Release artifact signed with production key.
- Backend config, rules and indexes verified.
- Purchase/backup/deletion integrations exercised in appropriate environment.
- Supported-device matrix tested.
- Privacy/legal copy approved.
- Monitoring, incident response and rollback rehearsed.

### Gate D — Production operation
- Crash, latency, notification and sync failure trends reviewed.
- Support and deletion requests handled to policy.
- Security/dependency findings triaged.
- Restore and disaster-recovery exercises scheduled.
- Feature flags and staged rollout allow safe disablement.

## 6. Current verification limitation

The discovery and source audit is remote repository inspection. The latest main-branch GitHub Actions run 45245907678 on commit 644571f68be024423376749fe8e50e1db02ff018 passed all Flutter, backend and Hosting CI jobs; it ran 460 Flutter tests, 11 backend Vitest files, 2 Firestore-emulator test files and 12 Hosting Node tests, and built both Android flavors. CI does not substitute for physical-device tests, live Google OAuth/Play Billing/Gemini/Stripe/Firestore flows, production purchase validation, Firebase or Hosting deployment attestation, or physical notification/SMS delivery.

## 7. People/Birthday architecture verification

The Phase 2.4 [verification and implementation gates](phase-2.4-architecture-design/verification-and-implementation-gates.md) translate approved Phase 2.3.1 rules and Phase 2.3 acceptance gaps into unit, repository, migration, import, timezone, transaction-failure and device-level obligations. These are planned tests, not evidence that they have run.
