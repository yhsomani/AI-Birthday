# RelateAI Implementation Tasks

Version: 1.0.0
Date: 2026-06-26
Source documents: [PLAN.md](PLAN.md), [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md), [SSOT.md](SSOT.md)
Progress log: [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)
Status: Micro-task execution backlog

## 1. How to Use This Backlog

Each task is intended to be small enough for one focused PR. A task is complete only when its acceptance criteria and validation command are satisfied. If a task reveals a bigger design issue, add a follow-up task instead of expanding the PR without bounds.

Task fields:

- ID: stable reference.
- Scope: product or code area.
- Work: the smallest useful change.
- Acceptance: observable done state.
- Validate: command or inspection that proves the task.

Default validation after any code task:

```bash
./gradlew testDebugUnitTest --no-configuration-cache
```

Current environment blocker: the local shell cannot locate a Java runtime. Complete T000 before relying on Gradle validation.

## 2. Phase Order

| Phase | Goal | Exit criteria |
| --- | --- | --- |
| P0 | Build environment and docs alignment | Java works; active docs point to RelateAI |
| P1 | Automation safety | No early sends; dispatch policy unified |
| P2 | AI contract correctness | Event-aware fallback and classification schema fixed |
| P3 | Data integrity | Event merge, date validation, regeneration, and no-route states fixed |
| P4 | Security and recovery | Backup v2, random DB key plan, redaction, pin guard |
| P5 | Product UX | Home, Contacts, Events, Messages, AI Doctor show next actions |
| P6 | Architecture cleanup | Workers reuse use cases, raw strings reduced, oversized files split |
| P7 | Runtime release validation | Device/emulator smoke, widget/deep link, live integration checklist |

## 3. P0: Build Environment and Docs Alignment

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T000 | Environment | Configure JDK 21 so Gradle runs in this shell | `java -version` reports JDK 21 or compatible, Gradle starts | `java -version && ./gradlew --version` |
| T001 | Test baseline | Run unit test baseline without code changes | Baseline result is recorded with failing tests listed, if any | `./gradlew testDebugUnitTest --no-configuration-cache` |
| T002 | Docs | Keep `docs/startup-idea/*` clearly archived as reference-only ideation | Active docs no longer imply LeadRescue AI is this product | Inspect archive notes and search non-archived docs for LeadRescue/product confusion |
| T003 | Docs | Add a short docs index or update SSOT links to include blueprint and tasks | A reader can find `PLAN.md`, `PRODUCT_BLUEPRINT.md`, and this backlog from canonical docs | Inspect `SSOT.md` or docs index |
| T004 | Docs | Mark `PLAN.md` P0 items as the release-blocking stabilization scope | P0 items are listed in one place with owner area and tests | Inspect `PLAN.md` |
| T005 | CI | Confirm CI uses JDK 21 and same Gradle commands | Local and CI validation commands match | Inspect [.github/workflows/android.yml](.github/workflows/android.yml) |

## 4. P1: Automation Safety

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T100 | Tests | Add unit test proving future `APPROVED` message does not dispatch before `scheduledForMs` | Test fails on current behavior before fix | Targeted test |
| T101 | Domain | Add `DispatchEligibilityPolicy` sealed decision model | Policy compiles without worker integration | Unit test compile |
| T102 | Domain | Implement decision for `APPROVED` before schedule | Returns `DeferUntil(scheduledForMs)` | Policy unit test |
| T103 | Domain | Implement decision for `APPROVED` at/after schedule | Returns `SendNow` if route/schedule gates pass | Policy unit test |
| T104 | Domain | Implement decision for `SMART_APPROVE` pending before schedule | Returns `NeedsApproval` or `DeferUntil`, never `SendNow` | Policy unit test |
| T105 | Domain | Implement decision for `SMART_APPROVE` pending at/after schedule | Returns `SendNow` when not rejected and gates pass | Policy unit test |
| T106 | Domain | Implement decision for `VIP_APPROVE` pending before deadline | Returns `NeedsApproval` | Policy unit test |
| T107 | Domain | Implement decision for `VIP_APPROVE` after deadline | Returns `Expire` | Policy unit test |
| T108 | Domain | Implement decision for `ALWAYS_ASK` pending | Returns `NeedsApproval`, never auto-send | Policy unit test |
| T109 | Domain | Add duplicate-state handling for `SENT` and `DISPATCHING` | Returns blocked/idempotent decision | Policy unit test |
| T110 | Data worker | Update `MessageDispatchWorker` to call `DispatchEligibilityPolicy` before any send | Worker no longer sends directly based only on `APPROVED` | Worker tests |
| T111 | Scheduler | Change WorkManager fallback to use initial delay when exact alarm is unavailable | Future schedules enqueue delayed work, not immediate work | Scheduler test |
| T112 | Scheduler | Keep immediate enqueue only when `scheduledForMs <= now` | Past due messages still dispatch promptly | Scheduler test |
| T113 | Domain use case | Update `DispatchMessageUseCase` to use policy instead of requiring status `APPROVED` only | Smart approve behavior matches worker | Use case tests |
| T114 | Notifications | Ensure approval notification actions use the same eligibility rules | Approve action schedules/defer-sends correctly | Receiver tests |
| T115 | Activity log | Log defer, expire, blocked, and send decisions with redacted details | Activity history explains dispatch outcome | Repository/unit test |
| T116 | Regression | Add test for exact-alarm denial plus approved future message | Proves D-001 cannot regress | Test suite |
| T117 | Regression | Add test for Smart Approve pending auto-send at scheduled time | Proves D-002 resolution | Test suite |
| T118 | Docs | Update `PLAN.md` debt D-001/D-002 status after code lands | Debt entry references fix PR/test | Inspect `PLAN.md` |

## 5. P2: AI Contract Correctness

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T200 | Tests | Add failing test for anniversary fallback through `AiServiceImpl.generateMessage` | Current default birthday fallback is exposed | Targeted test |
| T201 | AI service | Pass event type to `ResponseParser.parseMessageVariants` in generate path | Event-specific fallback used | AI service test |
| T202 | AI service | Pass event type to parser in regeneration path | Regenerated fallback respects event type | AI service test |
| T203 | Parser | Add tests for birthday, anniversary, work anniversary, revival fallback | All event fallbacks stable | `ResponseParserTest` |
| T204 | Parser | Return structured parse metadata for malformed JSON | Caller can log fallback reason | Parser/service tests |
| T205 | Logging | Log malformed AI responses without message body or secrets | Redacted structured log exists | Unit/static inspection |
| T206 | Prompt | Add `communication_style` to classification prompt JSON schema | Prompt and parser fields match | `PromptBuilderTest` |
| T207 | Parser | Parse `communication_style` with enum normalization | Valid style round trips | `ResponseParserTest` |
| T208 | Parser | Handle old field names or missing style safely | Missing style defaults intentionally with telemetry | Parser test |
| T209 | Domain | Ensure `ClassifyContactUseCase` persists parsed style correctly | Contact update includes style | Use case test |
| T210 | AI contract | Add central constants or model class for classification field names | Prompt/parser cannot drift silently | Compile/test |
| T211 | Docs | Update AI contract section in `PLAN.md` after fix | P0 AI items include status and tests | Inspect docs |

## 6. P3: Data Integrity

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T300 | Tests | Add manual birthday then discovery duplicate regression test | Current duplicate risk is captured | Use case test |
| T301 | Domain | Define canonical event identity function | Same contact/type/date maps to stable key | Unit test |
| T302 | Domain | Update manual event save to use or merge canonical event when appropriate | Manual birthday and discovery produce one event | Use case test |
| T303 | Domain | Preserve manual verification when imported event matches | User-verified data wins | Use case test |
| T304 | Domain | Add conflict outcome for same event type with different date | Conflict is visible, not silently overwritten | Use case test |
| T305 | Date handling | Replace lenient `Calendar` event date calculation with validated date logic | Invalid dates rejected/quarantined | Unit test |
| T306 | Date handling | Add leap-day behavior test for non-leap years | Feb 29 behavior is intentional | Unit test |
| T307 | UI | Show event source and verification status in event detail/list row | User can see manual/imported/merged/conflict | UI test or screenshot |
| T308 | Channel | Change `AutoSendChannelSelector` to return typed route result | No available route is representable | Unit test |
| T309 | Channel | Update generation path to store blocked/no-route readiness instead of fallback SMS | Draft cannot be auto-ready with no route | Use case test |
| T310 | Dispatcher | Align generation route result with runtime `DeliveryChannelResolver` | Generation and dispatch agree | Unit tests |
| T311 | Regeneration | Re-run quality gate after regeneration | Quality score and fallback metadata refresh; blank/invalid or no-route regeneration stays review-first while nonblank Fully Auto drafts can remain automatic | Use case test |
| T312 | Regeneration | Recompute route readiness after regeneration | Stale channel readiness is not preserved | Use case test |
| T313 | Regeneration | Preserve user edits only when explicitly requested | Regeneration clears stale edited text safely | Use case test |
| T314 | Contacts sync | Encode People API page and sync tokens | URL is valid for special token characters | Unit test |
| T315 | Contacts sync | Return permission-specific outcome when device contacts denied | UI can show permission action | Use case/repository test |
| T316 | Docs | Update data integrity section in `PLAN.md` | D-005, D-006, D-008, D-009 tracked | Inspect docs |

## 7. P4: Security and Recovery

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T400 | Backup | Define `BackupManifest` model with version, app version, counts, checksum | Manifest serializes in backup preview tests | Unit test |
| T401 | Backup | Add backup include list for relationship data | Activity logs and feedback included | Backup test |
| T402 | Backup | Add backup preference subset for non-secret settings | Quiet hours, language, channel blackout included | Backup test |
| T403 | Backup | Add backup exclude tests for OAuth, API key, Gmail password, DB key | Secrets absent from JSON | Backup test |
| T404 | Backup | Add import preview before DB mutation | Preview shows counts and version | ViewModel/service test |
| T405 | Backup | Ensure restore transaction rollback on invalid payload | DB unchanged on failure | Backup test |
| T406 | Backup | Remove or clean internal temp `.enc` file after URI export | No sensitive leftover file after successful export | Service test |
| T407 | Backup | Add restore mode decision: replace first, merge later | UI copy and service behavior agree | Test/inspection |
| T408 | Security | Design random SQLCipher key migration from deterministic key | Migration states documented and tested with fakes | Unit test plan |
| T409 | Security | Generate random DB key for new installs | New key is not derived from Android ID | Unit test |
| T410 | Security | Wipe key and close DB on sign-out/data purge | Reopen cannot access old data | Unit/integration test |
| T411 | Security | Add pin-expiry release guard | Build/check fails inside release support window | Gradle/script test |
| T412 | Logging | Add redaction tests for tokens, phone numbers, emails, message bodies | Structured logs are safe by default | Unit tests |
| T413 | Deep links | Verify biometric lock gates deep-linked routes | Locked user cannot bypass auth | Navigation test |
| T414 | Docs | Update backup/security docs and strings | Backup language no longer claims device-ID-derived recoverability as desired | Resource/doc inspection |

## 8. P5: Product UX

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T500 | Home | Add ranked next-action model | Home can show one primary action and supporting actions | ViewModel test |
| T501 | Home | Surface stale/never backup status on Home | Backup action appears when stale | UI/ViewModel test |
| T502 | Home | Surface setup blocker summary from AI Doctor | User sees top blocker | ViewModel/UI test |
| T503 | Home | Add low-health relationship action | Action routes to contact or revival draft | ViewModel test |
| T504 | Contacts | Add contact quality state model | Ready/missing event/missing channel/missing context are computed | Unit test |
| T505 | Contacts | Add filters for missing relationship, missing channel, low health, VIP | Filters are testable and labeled | UI test |
| T506 | Contact detail | Group fields into essentials, personalization, automation, history | Screen is easier to scan | UI inspection/test |
| T507 | Contact detail | Show personalization quality impact | User sees next context improvement | ViewModel/UI test |
| T508 | Events | Show source, verification, and conflict state | Event list explains trust level | UI test |
| T509 | Events | Add duplicate/conflict resolution action | User can merge or keep separate | Use case/UI test |
| T510 | Messages | Split tabs into Needs review, Scheduled, Blocked, Sent, Failed | State-specific task flow exists | UI test |
| T511 | Messages | Add readiness reason badge to each row | User knows why blocked/scheduled | UI test |
| T512 | Wish preview | Show event type, route, schedule, approval mode, quality/fallback | User sees send risk clearly | UI test |
| T513 | Wish preview | Recalculate readiness after edit | UI updates after edit | ViewModel test |
| T514 | AI Doctor | Rank setup blockers and show single recommended fix | Top fix is deterministic | ViewModel test |
| T515 | AI Doctor | Add "generic messages" diagnostic from personalization quality | User gets context improvement actions | ViewModel test |
| T516 | Backup UI | Add import manifest preview | User sees what restore will change | UI/ViewModel test |
| T517 | Activity history | Add filters for dispatch, AI, sync, backup, settings | Audit trail is usable | UI test |
| T518 | Localization | Convert remaining hardcoded ViewModel/use case user strings to resources | Parity tests pass | Localization tests |
| T519 | Accessibility | Add labels to icon-only actions in new/refactored UI | Screen reader actions are named | UI inspection/test |

## 9. P6: Architecture Cleanup

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T600 | Workers | Move message generation worker logic behind domain use case/service | Worker no longer duplicates prompt/quality policy | Worker/use case tests |
| T601 | Workers | Move dispatch worker decision logic behind domain policy | Worker is orchestration only | Worker tests |
| T602 | Models | Replace raw approval mode strings with `ApprovalMode` at domain boundary | Raw strings isolated to persistence mapping | Unit tests |
| T603 | Models | Replace raw channel strings with `MessageChannel` at domain boundary | No magic channel strings in policies | Static search/tests |
| T604 | Models | Replace raw message status strings with `MessageStatus` at domain boundary | Status transitions are typed | Unit tests |
| T605 | Models | Remove or migrate dead `AutomationMode.kt` sealed classes | No duplicate taxonomy remains | `rg "AutomationMode|CommunicationChannel"` |
| T606 | Domain boundary | Decide whether Room entities stay in domain or move to data | Decision recorded; code follows decision | ADR/doc and compile |
| T607 | Database | Split `AppDatabase.kt` migrations/converters/builders | Main DB file shrinks and behavior unchanged | Tests compile/pass |
| T608 | UI | Split `MessagesScreen.kt` into tabs, rows, dialogs, filters | Main file materially smaller | Compile/UI tests |
| T609 | UI | Split `ContactDetailScreen.kt` into sections | Main file materially smaller | Compile/UI tests |
| T610 | UI | Split `SettingsScreen.kt` into sections | Main file materially smaller | Compile/UI tests |
| T611 | UI | Split `GiftAdvisorScreen.kt` into sections | Main file materially smaller | Compile/UI tests |
| T612 | UI | Split `EventsScreen.kt` into sections | Main file materially smaller | Compile/UI tests |
| T613 | Error model | Introduce typed `RelateFailure` or equivalent | Use cases stop returning raw user strings | Unit tests |
| T614 | Error mapping | Add UI mapper from typed failures to `UiText` | Localized error text path exists | Unit tests |
| T615 | Networking | Inject shared People API HTTP client and request builder | Sync code becomes testable | Unit test |

## 10. P7: Runtime Validation and Release

| ID | Scope | Work | Acceptance | Validate |
| --- | --- | --- | --- | --- |
| T700 | Test | Run full local unit suite | Tests pass or known failures documented | `./gradlew testDebugUnitTest --no-configuration-cache` |
| T701 | Test | Run lint | Lint passes | `./gradlew lintDebug --no-configuration-cache` |
| T702 | Build | Assemble debug APK | APK builds | `./gradlew assembleDebug --no-configuration-cache` |
| T703 | Coverage | Generate Jacoco report | Report produced and reviewed | `./gradlew jacocoDebugUnitTestReport --no-configuration-cache` |
| T704 | Emulator | Run navigation smoke on emulator | Main routes render without crash | Connected test |
| T705 | Emulator | Validate deep links | Wish/contact/settings deep links route correctly and respect lock | Connected test |
| T706 | Emulator | Validate widget states | Empty/today/upcoming/pending states render | Connected/widget test |
| T707 | Device | Validate contacts permission denied/granted flows | User sees correct states | Manual/device script |
| T708 | Device | Validate exact-alarm denied flow | No early send occurs | Manual/device test |
| T709 | Device | Validate SMS with safe test recipient | Status updates are correct | Manual/device test |
| T710 | Device | Validate Gmail SMTP with test account | Test email and event email work | Manual/device test |
| T711 | Device | Validate WhatsApp automation only with explicit consent | Service behavior is understandable and reversible | Manual/device test |
| T712 | Backup | Validate export/import round trip on clean install | Relationship data restored, secrets excluded | Manual/device test |
| T713 | Security | Run secret/log redaction scan | No raw secrets in source/log fixtures | `rg` plus tests |
| T714 | Release | Validate release signing guard | Missing env vars fail release task cleanly | CI/local release guard |
| T715 | Release | Validate pin expiry guard | Guard passes for current release window | CI/local guard |
| T716 | Docs | Update release notes and known limitations | User-facing limitations are honest | Doc inspection |

## 11. Product Build Milestones with Micro Deliverables

### Milestone M0: Ready to Work

- Complete T000 through T005.
- Exit: Gradle can run and docs point to one product.

### Milestone M1: Safe Automation Core

- Complete T100 through T118.
- Exit: every send path uses one policy and cannot send before schedule.

### Milestone M2: Correct AI Outputs

- Complete T200 through T211.
- Exit: AI fallbacks and classification data are event-aware and schema-safe.

### Milestone M3: Reliable Relationship Data

- Complete T300 through T316.
- Exit: events do not duplicate, invalid dates do not roll, blocked routes are explicit, regeneration is safe.

### Milestone M4: Trust, Backup, and Security

- Complete T400 through T414.
- Exit: user can recover local relationship data without leaking secrets.

### Milestone M5: Best Product Experience

- Complete T500 through T519.
- Exit: Home, Contacts, Events, Messages, AI Doctor, and Backup guide users to the next best action.

### Milestone M6: Maintainable Codebase

- Complete T600 through T615.
- Exit: workers, policies, models, UI files, and errors are easier to evolve.

### Milestone M7: Release Candidate

- Complete T700 through T716.
- Exit: the app has passed local, emulator, device, security, and release-readiness validation.

## 12. First Ten Tasks to Start

Start here to get maximum product safety quickly:

1. T000: Configure JDK 21.
2. T001: Run test baseline.
3. T100: Add failing future-approved-message test.
4. T101: Add `DispatchEligibilityPolicy`.
5. T102: Defer approved messages before schedule.
6. T110: Integrate policy into worker.
7. T111: Delay WorkManager fallback.
8. T116: Add exact-alarm denial regression.
9. T200: Add failing event fallback test.
10. T201: Pass event type through AI service.

## 13. Completion Rules

A milestone is not complete because code was changed. It is complete only when:

- The listed tasks are done.
- Acceptance criteria are met.
- Targeted tests exist.
- Relevant Gradle validation runs.
- `PLAN.md` debt status is updated if the task resolves a debt item.
- No user-facing behavior regresses against [PRODUCT_BLUEPRINT.md](PRODUCT_BLUEPRINT.md).
