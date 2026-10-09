# 06 — Full-Product Reconstruction Review

**Status:** Council review complete; P0/P1 tranche 1 implemented and committed.
**Method:** six independent specialist reviews (product/business, architecture, security/privacy, UI/UX/accessibility, performance/reliability, platform/DevOps) run in parallel, then every load-bearing claim verified against code and tests before implementation. Findings below carry `file:line` evidence; severity is *confirmed* unless marked otherwise.

---

## 1. Strategy decision (Architecture Decision Record 06)

**Decision: Structured modernization (Strategy A/B hybrid) — retain the architecture, fix the confirmed defects, do not rewrite.**

Rationale:
- The layered feature-based layout, dependency direction (domain never imports data/presentation), Riverpod state graph, Drift local-first core, durable job worker, and test discipline (372 passing tests, contrast + overflow gates) are sound and were confirmed by all six reviews. A greenfield rebuild would buy nothing and risk regressing working behavior.
- The real gaps are (a) one duplication root cause (two Person model/persistence stacks), (b) several silent no-ops and dead paths, (c) release-blocking config mismatches, (d) privacy/backup posture gaps. All are fixable in place with small, gated commits.
- The "ideal greenfield" version (single Person model, one write path, honest statuses, truthful privacy copy) informed the fixes below; it did not justify a rewrite.

**Top architecture debt (retained for a dedicated refactor, see backlog P1-06):**
the app has two live Person models — legacy `features/people/domain/person.dart` + `data/person_repository.dart` (write path via `PersonService`) and the feature model `features/people/domain/models/person.dart` + `domain/repositories/people_repository.dart` (read path via `peopleStreamProvider`, used by dashboard/history/people). Both map to the same `persons` table; enums differ in semantics. This split-brain is the root cause of several findings and must be unified to a single model and single persistence layer.

---

## 2. Council findings → backlog

| ID | Area | Finding (evidence) | Severity | Status |
|---|---|---|---|---|
| P0-01 | Subscription | **Play package-name mismatch.** Android app is `com.yashsomani.ai_birthday` (`android/app/build.gradle.kts:28`) but the client constant and server `EXPECTED_PACKAGE_NAME` were `com.yashomani.ai_birthday` (`lib/features/subscription/application/subscription_service.dart:75`, `backend/functions/src/services/subscriptionVerification.ts:18`). Both sides agreed with each other, so `PACKAGE_MISMATCH` never fired — instead the server called Google Play's purchases API with a nonexistent package → 404 → every purchase verified as `none`. | Critical (release-blocker) | ✅ committed |
| P0-02 | Android backup | **System auto-backup enabled with no exclusions** (`android/app/src/main/AndroidManifest.xml:9-12`): the Drift DB (names, phones, emails, notes, facts, draft bodies) and shared-preferences (incl. the encrypted secure-storage blob) are copied to Google cloud backup, contradicting the local-first/opt-in privacy posture. | High | ✅ `android:allowBackup="false"` committed |
| P1-03 | AI reliability | **No timeout on AI generation.** `AppFailureCode.aiTimeout` existed (`lib/core/errors/app_failure.dart:26`) but no AI call had a bound — a stalled provider held a durable job in `running` forever. | High | ✅ 30s bound in `AiRouter` (mono-choke-point, covers user-key + Nano + variations) + test |
| P1-04 | AI correctness | **"Turning age" used `DateTime.now().year`** (`ai_prompt_builder.dart:55`) instead of the birthday cycle year, so a draft generated for next year's celebration printed the current year's age. | Medium | ✅ `targetCycleYear` threaded through job payload (durable across restarts) + studio + test |
| P1-05 | Reminders | **Imports never rescheduled reminders.** CSV/device-contacts imports batch-write through repositories (`people_screen.dart:342-377`), bypassing `PersonService.onChanged`; imported people got no reminder alarms. | High | ✅ shared `resyncReminderSchedule` called after import (covers CSV + device paths) |
| P1-06 | Architecture | **Person model/persistence duplication** (see §1). Both stacks are live; enum semantics diverge; restore path can round-trip a model that the write path rejects. | Critical | ⏳ backlog — dedicated refactor |
| P1-07 | Sync/data | **Birthday deletions never reach the cloud.** `deletePerson` tombstones the person but `deleteBirthday` hard-deletes the birthday row (`core/database/drift_repositories.dart:236-240`); sync writes are update-only and restore re-inserts the stale birthday, resurrecting it as an orphan pointing at a tombstoned person. The `Birthdays` table has no `deletedAt` column. | High | ⏳ backlog — schema migration |
| P1-08 | Product | **"Prepare drafts automatically" toggle is a silent no-op.** The form promises auto-prepare (`person_form_screen.dart:643-654`) and the flag is persisted + cloud-synced (`core/database/drift_repositories.dart:81,150`, `cloud_sync_service.dart:210,568`) but nothing consumes it. | High | ⏳ backlog — needs product decision (implement at 7-day lead vs remove) |
| P1-09 | Studio/quota | **"Generate variations" bypasses the durable job queue**: 3 parallel direct API calls (`message_studio_screen.dart:669-682`), results lost on unmount, 3× quota, no retry/stale guards. | Medium | ⏳ backlog — route through the job worker |
| P1-10 | AI fallback | **Quota (429) doesn't fall back.** `aiQuotaExceeded` is a hard failure; with the user's own key it could route to Nano when available. | Medium | ⏳ backlog |
| P1-11 | Sync | **No-op incremental backup still writes the reminder-settings row every time** (row lacks a timestamp); the zero-round-trip test is scoped to person-only state. Documented limitation, not a lie. | Low | ⏳ backlog |
| P2-12 | A11y | **Raw `Colors.green`/`Colors.grey` used for status semantics** (e.g., "Saved" in the studio) instead of `AppTone`; grey-on-dark fails AA. | High | ⏳ backlog |
| P2-13 | Design system | **Three parallel token systems with conflicting values**: `lib/ui/design_system/app_tokens.dart` (`#FFF9F5`, lg=16) vs `lib/shared/design_system/app_colors.dart` (`#FAF7F2`) + `app_spacing.dart` (lg=20) vs `docs/ui-ux/design-system.md` specs. | High | ⏳ backlog |
| P2-14 | Truthfulness | **Emoji status copy** ("Message refined ✨", "drafted ✨") violates the documented no-emoji rule; replaced with plain text in Status cluster. | Medium | ⏳ backlog |
| P2-15 | Privacy | **Public privacy page claimed Gemini receives "only a generic template-writing prompt" and contact details "are not sent"** — false: name/relationship/closeness/facts/draft are sent to the user's own key (`ai_prompt_builder.dart:49-60`). | High | ✅ Gemini section corrected (EN+HI); page still carries legacy "Birthday Autopilot" brand elsewhere — pre-release hosting |
| P2-16 | Backend/security | **Firestore backup writes are not server-validated** (client-shaped docs accepted under the owner path). | Medium | ⏳ backlog |
| P2-17 | Logging | **Redaction covers params only; free-text message bodies in log strings are not redacted.** | Medium | ⏳ backlog |
| P2-18 | Tests | **No direct unit tests** for `gemini_nano` platform provider and `sms/native_share` delivery services. | Low | ⏳ backlog |
| P2-19 | DevOps | Hosting web app (`backend/hosting`) not built/tested in CI; no release pipeline (backend is explicitly pre-deployment). | Medium | ⏳ backlog |
| P2-20 | DevOps | `ttl-policies.json` vs `firestore.indexes.json` drift; TTL policy values not enforced by tests. | Low | ⏳ backlog |
| P2-21 | Docs | `PROJECT.md` M3 still describes an "end-to-end encrypted zero-PII envelope" that was deferred; actual behavior is owner-scoped plaintext opt-in backup (SSOT is canonical). | Low | ⏳ backlog |
| P3-22 | UX | Settings opened from Message Studio uses `push` (stacks shell) instead of tab switch; People list "+" affordance duplicated with app-bar add. | Low | ⏳ backlog |

---

## 3. Tranche-1 implementation (this review)

| Commit subject | Files | Gate |
|---|---|---|
| fix(subscription): correct Play package name to com.yashsomani.ai_birthday | `subscription_service.dart`, `subscriptionVerification.ts`, `subscription_verification_test.dart` | analyze 0 · 372 flutter tests · 75 backend tests |
| fix(android): disable system auto-backup to keep data local by default | `AndroidManifest.xml` | analyze 0 · full suite |
| fix(ai): bound AI generation with a retryable timeout in AiRouter | `ai_router.dart` + timeout test | analyze 0 · full suite |
| fix(ai): use the birthday cycle year for "Turning age" | `ai_prompt_builder.dart`, `ai_job_handler.dart`, `message_studio_screen.dart` + 2 tests | analyze 0 · full suite |
| fix(reminders): resync reminder plan after CSV/device imports | `person_providers.dart`, `people_screen.dart` | analyze 0 · full suite |
| docs(privacy): correct Gemini data-sharing disclosure (EN+HI) | `backend/hosting/privacy/index.html` | backend suite unaffected (static) |

## 4. Validation report

- `flutter analyze --no-pub`: 0 issues.
- `flutter test --no-pub`: **372 passed** (369 prior + 3 new: router timeout, prompt cycle-year, payload cycle-year round-trip/fallback).
- `npm test` (backend/functions, non-emulator): **75 passed** (11 files) incl. `subscriptionVerification.test.ts` against the corrected package name.
- Claims that did not survive verification: "chips are sub-48dp touch targets" — false; M3 chips inherit `theme.materialTapTargetSize` (`MaterialTapTargetSize.padded` = 48dp) from `chip.dart:1295-1299`, so no chip theme change was made.

## 5. Next steps (in priority order)

1. **P1-06** Person model unification — largest remaining architecture debt; sequence after it: P1-08 (auto-prepare) and P1-09 (variations) become routine.
2. **P1-07** Birthday tombstone migration + sync delete propagation.
3. **P2-12/13** a11y status colors + token consolidation (share one token source with the redesign doc).
4. **P2-14/15** honest status copy + finish privacy page cleanup for the actual product name.