# Verification, evidence, coverage and analysis ledger

[Knowledge home](../README.md) · [Features](../features/README.md) · [Business rules](../business-rules/README.md) · [Traceability](../traceability/README.md)

**Repository revision reviewed:** `2abcc1aaa24a69c0aff5bddbc13c5f05d0ed14f8` (merged PR #340). **Review date:** 2026-10-14. **Change mode:** documentation only. Existing 50-node registry remains the canonical feature inventory. This extension adds rules, source mappings, eight individual deep-feature READMEs and a full route catalog, but does not claim an exhaustive atomic feature census.

## Independent CI observation — important current status

- [GitHub Actions run 38594418108](https://github.com/yhsomani/AI-Birthday/actions/runs/38594418108) completed with **failure** at the merge commit. The **Flutter analysis and tests passed**, and the **Backend Security & Cloud Functions** and **Hosting** jobs succeeded.
- The **Flutter release APK build** step failed while evaluating `android/settings.gradle.kts` line 20: Gradle could not resolve the `dev.flutter.flutter-plugin-loader` dependency graph because `org.jetbrains.kotlin:kotlin-stdlib:2.2.21`, the Kotlin Gradle plugin BOM, and related artifacts were not found/resolved from the plugin repository. This is a directly observed build failure, **not evidence of the definitive root cause**. Check artifact repositories, plugin management resolution, transient availability and pinned build tooling before modifying production code.
- [Prior CI run 38591137923](https://github.com/yhsomani/AI-Birthday/actions/runs/38591137923) completed successfully at its earlier SHA. That does not override the latest failure or establish release signing, installation, deployed services or on-device Nano runtime.

**Link verification:** 247 relative Markdown references were structurally checked within the generated package, with 87 distinct references to pre-existing repository paths verified against the current 369-file GitHub tree (0 missing). Actual navigation/anchor rendering was not tested.

Read the [per-domain feature library](../features/deep-dives/README.md) and the [route catalog](../navigation/README.md) for more detailed traces. Source-path and parent-ID checks are provided by the dependency-free [validator](../tools/validate_knowledge.py).

## Execution/evidence separation

| Verification layer | Status in this run | What it supports |
|---|---|---|
| Remote repository tree and representative core source | Inspected | Source present, symbol path mapping, domain inventory |
| Existing 50-node feature registry | Inspected and reused | Stable feature IDs and available baseline mapping |
| Flutter source implementation and tests | Relevant source and test locations cross-checked; CI run observed | Static functional contracts and named test-suite presence |
| Latest GitHub Actions | Observed, build failed | Specific build-stage issue; passing other jobs |
| Physical Android emulator / device | Not available | No live notification/Nano/Play verification |
| Firebase staging/project deployment | Not accessed | No evidence of endpoint deployment or live rules effectiveness |
| Developer's local uncommitted Git state | Not accessible through remote connector | Cannot attest local worktree status |
| Additional local knowledge document links/JSON | Package validation | Relative link/JSON structure, not code correctness |

## High-value acceptance scenario matrix

| Scenario | Feature | Evidence location | Next verification method |
|---|---|---|---|
| New user gated to Onboarding | ONB-GATE | [router](../../../lib/app/router.dart) | Widget test and onboarding restart |
| Invalid birth date and absent year | PEOPLE-VALIDATE | [validator tests](../../../test/features/people/domain/person_input_validator_test.dart) | Unit tests plus UI errors |
| Paste CSV with quoted newline/formulas | PEOPLE-CSV | [CSV tests](../../../test/features/people/data/contact_csv_service_test.dart) | Round-trip and invalid-row assertions |
| Feb 29 in leap/non-leap years | BIRTH-CALC | [birthday engine tests](../../../test/features/birthdays/domain/birthday_engine_test.dart) | Timezone + daylight saving matrix |
| Quiet-hours suppression and defer | REM-RULES | [reminder tests](../../../test/features/reminders/domain/reminder_scheduler_test.dart) | Scheduler tests + native device check |
| Reboot/permission revoked exact alarms | REM-NATIVE | [native receiver](../../../android/app/src/main/kotlin/com/yashsomani/ai_birthday/BirthdayNotificationReceiver.kt) | Instrumented physical-device/emulator |
| BYOK succeeds without Pro | AI-BYOK | [router tests](../../../test/ai/ai_router_test.dart) | Fake provider + real controlled key |
| Forced Nano denied without entitlement | AI-NANO | [router tests](../../../test/ai/ai_router_test.dart) | Fake bridge + entitlement permutations |
| Timeout and durable AI retry | JOBS | [worker tests](../../../test/features/jobs/application/job_worker_test.dart) | Fault injection/cancellation test |
| Human edits while AI request pending | STUDIO-DRAFT | [optimistic tests](../../../test/features/message_studio/presentation/message_studio_optimistic_test.dart) | Slow provider result plus concurrent edits |
| WhatsApp link open is not sent | SEND-TRUTH | [WhatsApp tests](../../../test/delivery/whatsapp_handoff_test.dart) | Verify external return and explicit confirm |
| Backup partial generation write | CLOUD-UP | [Cloud sync tests](../../../test/features/sync/data/cloud_sync_service_test.dart) | Firestore emulator with fault injection |
| Restore malformed records | CLOUD-DOWN | [Cloud sync service](../../../lib/features/sync/data/cloud_sync_service.dart) | Incomplete manifest and skip/error cases |
| Purchase verification account binding | CTRL-PURCHASE | [backend tests](../../../backend/functions/test/subscriptionVerification.test.ts) | Test-server and Play sandbox |
| Firestore data ownership and deletion fencing | CLOUD | [emulator tests](../../../backend/functions/test/firestore.rules.emulator.test.ts) | Emulator rules with authenticated test users |
| Concurrent claim/arm sender racing | CTRL-CLAIM | [race/property tests](../../../backend/functions/test/races.property.test.ts) | Emulator plus deployed staging rehearsal |
| Web deletion ambiguity/replay recovery | WEB-DELETE | [Hosting tests](../../../backend/hosting/test/deletion-contract.test.mjs) | Staging with App Check/reauth |
| Android release APK resolution | APP | [CI run](https://github.com/yhsomani/AI-Birthday/actions/runs/38594418108) | Reproduce Gradle dependency resolution with logs |

## Source review ledger

| Area | Reviewed for this extension | Limitation / follow-up |
|---|---|---|
| App route graph | `lib/app/router.dart` | Route existence, not interactive journey completion |
| Forms, imports and validation | Validator, CSV, People presentation | Duplicate handling and cross-field semantics need characterization |
| Birthday/reminder domain | Engine, scheduler, quiet hours, lifecycle status | Device / lifecycle reconciliation not run |
| Message/AI/delivery | Studio, AI router, job worker, handoff builder, draft enums | No live external delivery or device model verification |
| Persistence and sync | Drift tables, CloudSyncService, Firestore rules | No staging or emulator run in this response |
| Billing and auth | Entitlement, subscription notifier, auth entry points | No Play sandbox, live token refresh or App Check verification |
| Firebase control plane | Callables, schemas and domain policy constants | Separate release status; mobile client integration not proven |
| Web Hosting | Package route contract from existing README | Legal/release gates remain open |
| Tooling and pipeline | GitHub Actions run and error logs | Build root cause not established |

## Audit conclusions and documentation coverage

- **Baseline:** 369 tracked files at the analyzed SHA; the merged knowledge portal originally had 50 feature nodes, 7 documented high-level journeys and 14 gap entries.
- **Extension inventory:** see counts in the companion package manifest; counts are taken from generated files rather than guessed.
- **No credible percentage:** feature completeness cannot be reported without an independently enumerated denominator of all substantial requirements and atomic units. Do not confuse linked test files with assertion coverage.
- **Known unresolved areas:** authoritative person model, automatic preparation pipeline, malformed backup restoration, app resume/timezone recalculation, actual backend deployment, physical device Nano and native notification behavior, and current Gradle build failure.

## Maintenance / follow-up priorities

1. Diagnose the concrete Gradle artifact resolution failure without assuming that the documentation PR caused it.
2. Run `flutter analyze` and `flutter test --exclude-tags golden`, and verify `flutter build apk --release` in a reproducible toolchain after fixing build resolution.
3. Exercise Firebase emulator suites, Play sandbox and physical Android notification/Nano cases independently.
4. Add exact named assertions to every high-impact business rule ID and refine the registry where proof changes.
5. Enforce document link/registry validation via lightweight CI only once it is tested on a full clone.

## Completion/blocked declaration

**Completed:** inspected available remote source and CI, documented detailed rules, requirement mappings, endpoint surface, data-lineage, eight individual feature-domain deep dives, ten declared UI routes, and test scenarios; assembled an offline documentation-only patch. **Blocked/not attempted:** production runtime observation, 100% file-by-file source inspection, independent multi-agent agreement, automatic commits or remote pushes, live deployment checks. Neither code nor production behavior was modified.
