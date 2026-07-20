# Birthday Autopilot UI Simplification — Final Implementation Plan

## Outcome and scope

Simplify the production React Native UI around three jobs: Home answers “is my edition ready and what happens next?”, People manages explicitly selected recipients, and Settings changes the plan/account or opens support. Remove duplicate entry points, dashboards, raw diagnostics, vanity metrics, and competing actions while preserving every binding MVP capability and safety/data/platform contract.

In scope: production `src/features/live/` presentation and task-leaf routing, directly coupled localization, focused tests, navigation/accessibility contracts, and rendered validation. Out of scope: native or backend state-machine changes, ports/data models, permission semantics, claim/arm/reservation/deletion behavior, fixture-backed production substitutions, new features, a fourth tab, and broad documentation/prototype cleanup.

## Invariants for every slice

- Primary navigation remains exactly Home, People, Settings. Activity opens from Home; task, review, repair, privacy, support, diagnostic, and destructive flows are leaves.
- Loading, stale, error, unknown, wrong-platform, Android-managed, and deletion-in-progress states fail closed. Hiding detail never upgrades readiness.
- Android activation still requires current eligibility, immutable approval, successful bound TEST, background/network/SIM/cost review, and all native gates. Copy never claims carrier acceptance or delivery.
- iOS never exposes unattended sending, an Android permit path, or an auto-opened composer. MessageUI follows an explicit foreground review, revision/nonce/material checks, and the native reservation/CAS sequence; final content, sender, transport, acceptance, and delivery remain unknown to the app.
- Enrollment, approval/proposal invalidation, duplicate prevention, blocklist, pause, retention, deletion recovery, external-copy disclosure, and diagnostic redaction semantics do not change.
- Each state has at most one primary action. All blockers remain visible in plain language; status never relies on color.
- Every changed surface ships with English/Hindi key parity, locale-aware interpolation/bidi isolation, meaningful screen-reader semantics and focus, 48dp targets, 200% text support, dark/high-contrast behavior, reduced motion, and pseudo-RTL safety.
- No slice changes a native method order, payload, revision, nonce, reservation, approval hash, or production port unless a separately approved contract task names and proves the need.

## Dependency-ordered atomic backlog

### S1 — Prune Settings and make Help the support gateway

**Files:** `src/features/live/LiveSettingsScreen.tsx`, `src/features/live/LiveHelpLegalScreen.tsx`, `src/features/live/LiveAppShell.tsx`, `src/localization/liveResources.ts`, `src/localization/liveResources.test.ts`, `src/app/LiveApp.test.tsx`, `tools/live-navigation-contract.test.mjs`, `tools/ui-accessibility-contract.test.mjs`, `e2e/maestro-production-smoke/01-production-navigation.yaml`.

**Change:** remove Settings duplicates for Activity, Attention, Diagnostics, readiness, privacy inventory, static phone-following cards, and routine Refresh. Group retained rows into Birthday plan, Account and privacy, and Help. Keep Diagnostics reachable from Help; keep Activity/Fix issues from Home. Preserve conditional Android notification/device/transfer controls and iOS suppression.

**Dependencies:** none. This is the exact first slice specified below.

**Gate:** all removed destinations have tested replacement paths; no native/data mutation changes; Settings and Help render at 200% EN/HI without clipped rows. Rollback is presentation-only: restore former Settings entries while leaving native contracts/data untouched.

### S2 — Make Home the concise readiness control center

**Files:** `src/features/live/LiveHomeScreen.tsx`, `src/features/live/LiveProjectionState.tsx`, `src/localization/liveResources.ts`, `src/localization/liveResources.test.ts`, `src/app/LiveApp.test.tsx`, `tools/ui-accessibility-contract.test.mjs`, relevant Home Maestro flow.

**Change:** derive a stable priority order—blocking setup/repair, eligible due iOS review, paused, healthy—and show one readiness statement, next birthday, compact today/week/enabled/attention summary, and one contextual primary action. Keep Activity and master pause/resume secondary. Replace always-visible payload/heartbeats/coordination/horizon metrics with View message or Status details; blockers stay expanded. Remove normal-state Refresh, Message, and Automation button piles.

**Dependencies:** S1 replacement routes.

**Gate:** users can answer “ready?” and “who is next?” without technical codes; managed/unavailable iOS exposes no Review message; unknown/stale projections disable consequential actions; Android/iOS truth phrases remain exact. Roll back only Home composition/state priority.

### S3 — Make Attention the single plain-language repair list

**Files:** `src/features/live/LiveAttentionScreen.tsx`, `src/features/live/LiveProjectionState.tsx`, `src/localization/liveResources.ts`, `src/localization/reasonCopy.ts`, `src/app/LiveApp.test.tsx`, focused accessibility/navigation tests.

**Change:** retain account, contacts, approval/proposal, and platform grouping; show consequence plus one valid next action. Put reason codes/gate names behind Support details and keep non-actionable issues honest with wait/retry/help.

**Dependencies:** S2 Home-to-Attention entry.

**Gate:** every blocker route is reachable and returns correctly; raw codes are absent from the default reading order but available to support; no issue is presented as repaired before the projection confirms it.

### S4 — Extract the iOS due-composer job from reminder settings

**Files:** `src/features/live/LiveAutomationScreen.tsx`, new `src/features/live/LiveComposerReviewScreen.tsx`, `src/features/live/composerErrorCopy.ts`, `src/features/live/LiveAppShell.tsx`, `src/localization/liveResources.ts`, `src/app/LiveApp.test.tsx`, `tools/ios-companion-workflow-boundary.test.mjs`, `tools/live-navigation-contract.test.mjs`, relevant iOS Maestro flow.

**Change:** first characterize current proposal/revision/nonce/reservation/CAS call order and suppression. Then move only the existing due proposal review, final disclosure, explicit Review message action, cancellation/error explanation, and terminal outcome presentation into a Home/reminder-addressable leaf. Leave native orchestration and port calls unchanged; remove those controls from ordinary reminder settings.

**Dependencies:** S2 Home action and existing route-origin behavior.

**Gate:** notification/deep-link navigation revalidates the opaque request and current native projection; Android-managed/unavailable/unknown status exposes no composer; Cancel/Failed/Reported Sent/Unknown remain distinct; Reported Sent and Unknown cannot reopen the occurrence; Back/gesture/system Back agree. Roll back by restoring the presentation block to Automation, not by changing native state.

### S5 — Simplify platform sending/reminder settings

**Files:** `src/features/live/LiveAutomationScreen.tsx`, `src/features/live/LiveAndroidDeviceControls.tsx`, `src/localization/liveResources.ts`, `src/app/LiveApp.test.tsx`, Android/iOS workflow-boundary tests, focused rendered flows.

**Change:** show a compact platform-correct status and one contextual primary action. Android reveals the test-number form only when required or after Run another test and retains test/cost/SIM/activation/pause/transfer reviews. iOS retains permission, reminder activation, pause/resume, window and honest visibility state; healthy horizon internals move to support detail. Show transfer controls only in applicable standby/transfer/error states.

**Dependencies:** S4 removes the iOS composer job.

**Gate:** no Android activation without a valid TEST; Submitted and SentFromDevice copy remains distinct; iOS settings never imply automatic or exact-timed sending; platform-inapplicable actions are absent. Roll back per platform component without touching native operations.

### S6 — Focus message and schedule editing

**Files:** `src/features/live/LiveMessageScreen.tsx`, `src/features/live/LivePolicyEditor.tsx`, `src/features/live/LiveAppShell.tsx` only if an existing leaf cannot host schedule editing, `src/localization/liveResources.ts`, `src/localization/liveResources.test.ts`, focused component/accessibility tests.

**Change:** Message leads with current template, language, editor, and exact preview; Help me write reveals optional Gemini/tone/privacy content; Message options reveals generic-name fallback and segment cap. Schedule leads with a plain-language window summary and progressively reveals grace/cap while retaining the mandatory 400-day simulation and explicit save/review consequences.

**Dependencies:** S1 settings rows; do not add a new route until existing route behavior is evaluated.

**Gate:** default authoring works offline without Gemini; no contact PII reaches Gemini; material edits still invalidate approvals; unsafe policy cannot save; 30-minute/4-hour/same-day/cap rules and simulation remain enforced; each editor has one primary save/review action.

### S7 — Make setup four progressive, resumable steps

**Files:** `src/features/live/LiveSetupScreen.tsx`, `src/features/live/LiveProductSetupJourney.tsx`, `src/app/NativeAppBoundary.tsx`, `src/features/live/LiveBatchApprovalScreen.tsx`, `src/features/live/LiveAppShell.tsx`, `src/localization/liveResources.ts`, `src/app/LiveApp.test.tsx`, `e2e/maestro/01-fresh-setup.yaml`, large-text/localization setup flows.

**Change:** present only the current binding step: welcome/compatibility, Contacts disclosure/connection, people/message/window, then Android test+enable or iOS reminders. Collapse completed steps instead of duplicating dashboards. Finish later returns to truthful limited Home with Continue setup; external settings returns always reload native state. Do not invent persisted completion/deferral state without an existing safe projection.

**Dependencies:** S5 platform actions and S6 focused editors.

**Gate:** Contacts permission is never requested before disclosure; Android cannot complete without enrollment/approval/TEST/full readiness/activation; iOS never claims automation; deletion/account repair cannot be bypassed; Back and resume land on the current unfinished step.

### S8 — Simplify People discovery and person setup

**Files:** `src/features/live/LivePeopleScreen.tsx`, `src/features/live/LivePersonDetailScreen.tsx`, `src/localization/liveResources.ts`, focused list/detail tests and People Maestro flow.

**Change:** keep search and All/Enabled/Ready/Needs attention/Excluded filters, one explicit Sync contacts action, compact rows, and distinct empty/error/revoked states. Person detail selects one job—set up, review changes, or view approved greeting—reveals ambiguous phone/date/leap choices only when needed, sequences enrollment then approval, and moves Pause/Exclude/Block under Manage this person with distinct consequences.

**Dependencies:** S6 message/window presentation.

**Gate:** search/filter behavior is unchanged; all contacts remain Off by default; enrollment and approval are separate explicit events; every material change invalidates approval; Pause, Exclude, and Block remain distinguishable and reversible where the contract allows.

### S9 — Make bulk approval counts authoritative

**Files:** `src/features/live/LivePeopleScreen.tsx`, `src/features/live/LiveBatchApprovalScreen.tsx`, `src/features/live/LiveAppPort.ts` only if the existing read-only projection cannot provide an exact stable candidate set, focused batch tests.

**Change:** compute or request the exact eligible set before showing a count, review the exact list and message policy, then use existing per-person operations. Bind confirmation to the reviewed version/set and report completed/skipped/failed without treating unprocessed people as approved.

**Dependencies:** S8. A port change requires a separate interface review and is not assumed.

**Gate:** total contacts are never labeled ready; stale reviewed sets fail closed; partial results are truthful; retry cannot duplicate approval or sending state. Roll back the bulk entry while preserving individual enrollment.

### S10 — Consolidate Activity, Privacy, and support detail

**Files:** `src/features/live/LiveActivityScreen.tsx`, `src/features/live/LivePrivacyScreen.tsx`, `src/features/live/LivePrivacyInventory.tsx`, `src/features/live/LiveCloudPrivacyBoundary.tsx`, `src/features/live/LiveHelpLegalScreen.tsx`, `src/features/live/LiveDiagnosticsScreen.tsx`, `src/localization/liveResources.ts`, privacy/activity/accessibility/navigation tests and Maestro privacy flow.

**Change:** use compact Activity rows with outcome-specific detail/recovery and place Clear activity beside the history it affects. Replace Privacy's radio menu with grouped, action-specific rows and focused consequence reviews while retaining every current action and `prepareAction -> confirmAction` flow. Show inventory/cloud boundary once in Privacy. Help retains legal/external-deletion paths and Diagnostics; Diagnostics retains explicit Preview then Share and technical codes.

**Dependencies:** S1 support routing and S3 support-detail pattern.

**Gate:** Android Submitted/Sent/Delivered/Unknown and iOS composer outcomes stay distinct; Clear activity discloses and preserves the Android 400-day safety projection or iOS terminal marker; deletion unknown/draining blocks conflicts; account deletion cannot be confused with sign-out/local wipe; external-copy and offline truth remain visible; diagnostics cannot share before preview and remains PII-free.

### S11 — Final cross-surface release audit

**Files:** `src/localization/liveResources.ts`, `src/localization/liveResources.test.ts`, `tools/ui-accessibility-contract.test.mjs`, `tools/live-navigation-contract.test.mjs`, `tools/screen-crosswalk.test.mjs`, `src/app/LiveApp.test.tsx`, relevant Maestro flows, `stitch/SCREEN_MANIFEST.md` only for actual route/file crosswalk changes.

**Change:** remove remaining routine internal terminology, dead localization keys, and obsolete navigation assertions; verify every removed surface has a replacement and every retained MVP job has acceptance evidence. Do not broaden into documentation or fixture cleanup.

**Dependencies:** S1–S10; each prior slice must already have its own local tests.

**Gate:** exactly three tabs; no raw contract key/reason code outside support detail/Diagnostics; English/Hindi parity and human review; Android/iOS truth assertions; screen-reader/keyboard/switch focus; 48dp, 200%, light/dark/high contrast, reduced motion, and pseudo-RTL; no critical/high accessibility finding.

## Exact first implementation slice: S1

### Required edit order

1. In `src/app/LiveApp.test.tsx` and `tools/live-navigation-contract.test.mjs`, lock the before/after reachability contract: three tabs; Home opens Activity and Attention/Fix issues; Settings opens Message, Schedule, platform sending/reminders, Privacy, and Help; Help opens Diagnostics; Back returns to the visible origin.
2. In `LiveSettingsScreen.tsx`, remove the direct Activity, Attention, and Diagnostics entries, duplicated readiness dashboard, duplicated privacy inventory/cloud boundary, static appearance/language/platform cards, and routine Refresh. Do not remove account identity, Privacy, Message, Schedule, conditional sending/reminder controls, notifications, Android sender/transfer state, or Help.
3. Render three semantic Settings groups: Birthday plan; Account and privacy; Help. Use localized row labels and platform-conditional descriptions; the hub has no filled primary action.
4. In `LiveHelpLegalScreen.tsx`, add the single Diagnostics support row while preserving Help, build/edition, legal, privacy, support, and external deletion destinations. Keep `LiveDiagnosticsScreen`'s route and Preview-then-Share behavior unchanged.
5. In `LiveAppShell.tsx`, change only entry wiring/origin handling needed for Settings -> Help -> Diagnostics and correct Back behavior; do not add a tab or change native route projections.
6. Update English and Hindi resources together; delete keys only after repository references prove they are unused. Update accessibility contract assertions for semantic grouped rows and unique labels.
7. Update the production navigation smoke flow to traverse Home -> Activity, Home -> Fix issues, Settings -> Privacy, and Settings -> Help -> Diagnostics on each applicable platform projection.

### First-slice invariants

- No native method, port payload, projection schema, mutation handler, approval, TEST, activation, pause, transfer, privacy, deletion, or diagnostic-share implementation changes.
- Activity and Attention remain reachable from Home before their Settings entries are removed.
- Diagnostics remains a leaf and still requires Preview before Share; it does not move into primary navigation.
- Android transfer/device ownership remains discoverable when applicable. iOS never renders Android controls.
- Legal, support, privacy, external deletion, and account/data controls remain reachable.
- Loading/error/unknown Settings state remains explicit; removal of the readiness dashboard does not present the account as healthy.

### First-slice acceptance and validation gates

- Static/component: focused `LiveApp` tests prove the route graph, platform suppression, unique accessible labels, and no removed Settings entry; localization tests prove exact English/Hindi key parity and interpolation safety.
- Contract: navigation contract proves exactly Home/People/Settings and origin-correct Back behavior; accessibility contract proves semantic headings/rows, 48dp targets, no duplicate focus targets, and no hidden primary action.
- Rendered Android: inspect Settings and Help at 100% and 200% text, English/Hindi, light/dark/high contrast, TalkBack focus order, and compact/large viewport; verify conditional sender/transfer rows in applicable projections.
- Rendered iOS: inspect the same matrix with VoiceOver and verify no Android language/control, no automation claim, and correct native-style Back behavior.
- Runtime: traverse every replacement path and ensure no blank/loading loop, stale origin, or unreachable leaf. Diagnostics Preview then Share remains unchanged.
- Regression: run the repository's focused test/typecheck/navigation/accessibility commands discovered at implementation time, followed by the available production smoke suite. Do not guess unavailable script names or treat a fixture-only pass as runtime evidence.
- Evidence classification: emulator/simulator results are recorded as such. Physical Android SMS/carrier, physical iPhone MessageUI, Firebase/App Check, distribution, and release gates are not changed by this slice and remain external/unavailable unless actually executed.
- Rollback gate: because the slice has no migration or native/data change, rollback restores the former Settings rows and direct Diagnostics entry. Never roll back by weakening route, privacy, diagnostic-redaction, or platform-suppression assertions.

## Completion gate

The simplification is complete only when every retained binding MVP job has a reachable, plain-language path; every removed surface has a tested replacement or is proven outside MVP; all platform/safety/privacy invariants remain green; and production rendered inspection passes the accessibility/localization matrix. A clean visual smoke run alone is not completion evidence.
