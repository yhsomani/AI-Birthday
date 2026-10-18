# Gap, contradiction, risk and improvement register

[Knowledge portal](../README.md) · [Architecture](../architecture/README.md) · [Features](../features/README.md)

Scope: evidence-based findings at `main` commit `75b9d91`. Priority is qualitative; *suspected* risk is not a confirmed bug. Old audit items are not automatically carried forward as current without re-verification.

| ID | Type / priority | Finding and evidence | Recommendation / verification |
|---|---|---|---|
| GAP-01 | Confirmed documentation contradiction · High | [AiRouter top comment](../../../lib/features/ai/domain/ai_router.dart) says entitlement is checked first, whereas `generate` actually obtains personal-key state first and allows BYOK without subscription, matching [SSOT §5](../../../SSOT.md). | Correct stale comment in later code-doc housekeeping; test BYOK-no-Pro and forced-Nano-without-Pro. |
| GAP-02 | Confirmed historical-document drift · High | [PROJECT.md](../../../PROJECT.md) marks M1–M5 PLANNED and mentions an AES-GCM backup envelope, while [CloudSyncService](../../../lib/features/sync/data/cloud_sync_service.dart) contains plaintext owner-scoped Firestore field snapshots; [ARCHITECTURE.md](../../../ARCHITECTURE.md) explicitly denies a zero-PII envelope. | Mark PROJECT.md historical, reconcile security/privacy descriptions; never claim end-to-end encrypted backups. |
| GAP-03 | Confirmed component size concern · Medium | [Settings](../../../lib/features/settings/presentation/settings_screen.dart) ~1,834 source lines; [Studio](../../../lib/features/message_studio/presentation/message_studio_screen.dart) ~1,487; [People](../../../lib/features/people/presentation/people_screen.dart) ~1,073. Their distinct responsibilities are intermixed. | Incrementally extract feature controllers/widgets only after characterization tests; retain UX contracts. |
| GAP-04 | Documented implementation gap · Resolved | [Previous remediation audit](../../audit/07-remediation-status.md) flagged two person models/repositories. Unified onto canonical [people/domain/models/person.dart](../../../lib/features/people/domain/models/person.dart) with full round-trip verification across `autoSendPolicy`, `deletedAt`, preferences and all enums in `test/core/database/drift_repositories_test.dart`. | Verified: all 437 tests pass, drift persistence preserves all fields and tombstones. |
| GAP-05 | Documented gap / unverified integration · High | [Person form](../../../lib/features/people/presentation/person_form_screen.dart) exposes `autoPrepare`; the older [remediation audit](../../audit/07-remediation-status.md) records automatic AI preparation as undecided/unimplemented in runtime. | Trace production triggers end to end; if absent, design opt-in consent/quota/error-aware preparation without autonomous WhatsApp send. |
| GAP-06 | Current release uncertainty · High | [backend README](../../../backend/README.md) and [Hosting README](../../../backend/hosting/README.md) explicitly say not deployed and require release configuration. | Separate source complete from deployed; publish release evidence after legal, identity, App Check, cloud and environment gates. |
| GAP-07 | Runtime/device verification missing · High | Android native [receiver](../../../android/app/src/main/kotlin/com/yashsomani/ai_birthday/BirthdayNotificationReceiver.kt), [MainActivity](../../../android/app/src/main/kotlin/com/yashsomani/ai_birthday/MainActivity.kt) and Nano adapter are source-present. Their physical-device correctness was not reproduced in this audit. | Test reboot rescheduling, alarm permission revoke/regrant, exact alarms, notification tap, Nano state transitions on supported hardware. |
| GAP-08 | Documented backup resilience concerns · Medium | [remediation audit](../../audit/07-remediation-status.md) lists malformed record handling and obsolete generation retention as remaining. `CloudSyncService` pre-validates all records before write and tracks skipped records with reasons. | Verified: pre-validation prevents local db corruption; skipped records tracked in CloudSyncResult; tests pass. |
| GAP-09 | Data lifecycle consistency risk · High | [Birthday lifecycle](../../../lib/features/birthdays/application/birthday_lifecycle_service.dart), [CloudSyncService](../../../lib/features/sync/data/cloud_sync_service.dart) and [reminder settings](../../../lib/features/reminders/application/reminder_settings_controller.dart) can update related local state. Prior audit notes foreground/timezone/restore reconciliation gaps. | Test midnight, timezone swap, app resume, restore and deletion races; verify reminders and recurring occurrences converge. |
| GAP-10 | Historical status may be stale · Medium | [docs/archive/IMPLEMENTATION_STATUS.md](../../archive/IMPLEMENTATION_STATUS.md) declares core phases complete at 2026-10-05 while [docs/audit/07-remediation-status.md](../../audit/07-remediation-status.md) lists remaining issues and newer code/tests exist. | Retain history but point readers to a dated live-source registry and CI SHA. |
| GAP-11 | Potential operational consistency risk · Medium | [Studio](../../../lib/features/message_studio/presentation/message_studio_screen.dart) mixes autosave and queued AI output; previous audit notes draft status remaining `ready` after handoff. | Verify latest optimistic edit revision and delivery-event transaction semantics, including rapid edits, retries and reopen. |
| GAP-12 | Separate backend-to-mobile integration ambiguity · Medium | Backend [callable surface](../../../backend/functions/src/functions/index.ts) includes claim/arm/sender transfer controls; current [mobile providers](../../../lib/app/providers.dart) show local-first studio/delivery. | Produce an endpoint caller matrix and test any real client wiring. Do not equate server coordination with automated WhatsApp sending. |
| GAP-13 | Quality gate / external verification · Medium | [CI workflow](../../../.github/workflows/ci.yml) runs Flutter, backend, hosting pipelines; Flutter SDK version pinned to 3.47.6 on stable channel. Goldens are excluded from Flutter CI. | Review goldens and physical-device scenarios; verify dependencies and deploy-time config without claiming CI failure. |
| GAP-14 | Design-system duplication · Resolved | Consolidated token ownership by re-exporting `app_tokens.dart` through [lib/shared/design_system/design_system.dart](../../../lib/shared/design_system/design_system.dart). Redundant direct token imports cleaned up. | Verified: 0 analyzer warnings, single canonical entrypoint established. |

## Proposed capability priorities (not implemented requirements)

1. **P0 verification:** execute release-oriented end-to-end checks for alarm lifecycle, Play purchase, Firebase backup/restore, web deletion and Gemini Nano hardware support; retain captured evidence.
2. **P0 integrity:** resolve recipient model duplication and test lossless import/export, backup, restore and tombstones.
3. **P1 consistency:** establish automatic-preparation product behavior and user-consent checks; avoid any implied silent personal WhatsApp send.
4. **P1 maintainability:** reduce large screens by separating presentation from orchestration without changing visible user journeys.
5. **P2 knowledge hygiene:** maintain stable IDs, update the registry in code changes, and reconcile historical documentation annotations.

## As-is versus specified versus recommended

| Capability | AS-IS source view | AS-SPECIFIED | TO-BE recommendation |
|---|---|---|---|
| AI entitlement | [Router](../../../lib/features/ai/domain/ai_router.dart) allows BYOK without app Pro; Nano requires Pro | [SSOT §5/§11](../../../SSOT.md) same | Align stale comments/tests, confirm device cases |
| Messaging | Studio calls external handoff and records user-confirmed send | [SSOT §9](../../../SSOT.md) user sends personally | Continue truthful labels; test foreground/background return |
| Cloud backup | Generation-based Firestore fields | [SSOT §19](../../../SSOT.md) local-first, server retention | Verify partial-failure recovery; do not claim zero-knowledge encryption |
| Auto-preparation | Preference exists; end-to-end scheduled trigger unverified | [SSOT §7/§16](../../../SSOT.md) preparation controls | Define opt-in, service entry point, budget, failure and retry rules |
| Coordination backend | Separate server-only source, not deployed per README | Backend documentation's coordination contract | Verify client caller(s), cloud config and production test boundaries |

**Security:** these are not penetration-test results. There is no claim that all vulnerabilities were found or that live Firebase access-control behavior was reproduced.

## Verification caveats

- Independent tool inspection established a [main CI success](https://github.com/yhsomani/AI-Birthday/actions/runs/38461085640) on the snapshot SHA.
- This knowledge-base generation performed remote source/document review only, no local Flutter, Dart, Kotlin, Node, Firebase emulator or physical device execution.
- Track findings as *confirmed*, *documented prior*, *inferred risk*, or *proposed*. Resolve prior findings against latest source before closing them.
