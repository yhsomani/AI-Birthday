# Overview

Simplify the production app into a three-tab control center with focused task leaves:

| Surface  | Core user job                                          | Focused leaves                                                               |
| -------- | ------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Home     | “Is Birthday Autopilot ready, and what happens next?”  | Activity, Attention/Fix, iOS Review message, setup continuation              |
| People   | Find someone and safely prepare their greeting         | Person setup/detail, batch approval                                          |
| Settings | Change the birthday plan, account/privacy, or get help | Message, schedule, Android sending/iOS reminders, Privacy, Help, Diagnostics |

The redesign should remove duplicated dashboards and internal implementation language, not capabilities. Android unattended sending still requires enrollment, immutable recipient approval, a successful test, permissions/readiness, carrier/SIM/cost awareness, and final claim/arm checks. iOS remains a foreground, user-confirmed `MessageUI` flow whose final content, sender, transport, and delivery are outside the app’s control. Privacy operations, retention, recovery, duplicate prevention, accessibility, localization, and platform-specific suppression remain binding.

# Scope

## In

- Production live React Native screens under `src/features/live/`.
- Three-tab navigation and task-leaf routing in `src/features/live/LiveAppShell.tsx`.
- Setup presentation in `src/app/NativeAppBoundary.tsx`, `LiveSetupScreen.tsx`, and `LiveProductSetupJourney.tsx`.
- Information architecture, hierarchy, progressive disclosure, labels, empty/error states, and action placement.
- English/Hindi production copy in `src/localization/liveResources.ts`.
- Component, navigation, localization, accessibility, crosswalk, and Maestro test updates.
- Small presentation-only component extraction needed to separate user jobs.

## Out

- Native sending, notification, scheduler, duplicate-prevention, reservation, CAS, ledger, deletion, or privacy semantics.
- Changes to native bridge method contracts or backend APIs unless an exact bulk-preview count proves impossible with existing read-only projections.
- New automation capabilities, social features, analytics, theme/language pickers, or speculative personalization.
- A fourth bottom tab.
- Broad rewrites of `PROJECT_ABOUT.md` or fixture/prototype screens.
- Removing safety disclosures, privacy actions, legal links, diagnostics export, or recovery paths.

# Current Evidence and Findings

1. **The navigation skeleton is already correct.**  
   `src/features/live/LiveAppShell.tsx:33-85` defines exactly Home, People, and Settings, with Activity, Person, Attention, Automation, Diagnostics, Help/Legal, Message, and Privacy as stack leaves. Preserve that model. `src/domain/navigation/model.ts` exposes the native `automation-review` and `attention` routes.

2. **Home contains nearly every system detail and too many competing actions.**  
   `src/features/live/LiveHomeScreen.tsx:448-859` combines readiness, next birthday, exact message, today review, pause confirmation, five count rows, contacts sync, scheduler heartbeat, coordination/sender state, extensive iOS reminder metrics, Activity, Fix, Message, Automation, Pause, and Refresh. The core question—ready and what next—is obscured.

3. **Settings duplicates Home, Attention, Privacy, and Activity.**  
   `src/features/live/LiveSettingsScreen.tsx:162-404` exposes five top-level buttons, static platform/appearance/language cards, a second readiness dashboard with technical codes, the complete privacy inventory, and Refresh.  
   `src/features/live/LiveAndroidDeviceControls.tsx:256-464` mixes normal notification settings with contextual sender-transfer operations.

4. **Automation mixes configuration, test, activation, health, and the iOS send job.**  
   Android content in `src/features/live/LiveAutomationScreen.tsx:384-641` keeps the complete policy editor, test-number form, test result, activation, pause, and reviews visible together.  
   iOS content in `LiveAutomationScreen.tsx:1077-1543` adds readiness codes, activation, policy, reminder metrics, proposal preparation, composer review, errors, and outcomes in one long screen. A time-sensitive “Review message” job is misplaced beneath reminder administration.

5. **The schedule editor exposes implementation metrics.**  
   `src/features/live/LivePolicyEditor.tsx:222-486` shows every policy field and simulation internals such as rolling caps and simulated days. The simulation is safety-critical, but its raw metrics are not a normal user job.

6. **Setup is presented as two overlapping dashboards.**  
   `src/features/live/LiveSetupScreen.tsx:433-692` always shows broad platform disclosures and multiple readiness summaries regardless of the current step.  
   `src/features/live/LiveProductSetupJourney.tsx:256-386` then repeats completed identity/contacts steps and presents several secondary destinations.  
   `src/app/NativeAppBoundary.tsx:20-82` stores “Finish later” only in component memory, so the journey returns after a restart.

7. **People search and filters are valuable; refresh and bulk truth need work.**  
   `src/features/live/LivePeopleScreen.tsx:453-706` has useful search and All/Enabled/Ready/Needs attention/Excluded filters. It also exposes both Sync and Refresh. Its bulk card uses an overall count while asynchronously scanning pages, so “Select all ready” can imply a count that has not yet been proven.

8. **Person detail presents every possible decision at once.**  
   `src/features/live/LivePersonDetailScreen.tsx:539-1028` shows resolved and unresolved phone/date choices, repeated platform warnings, separate enrollment and approval reviews, plus Pause, Restore, Exclude, Block, and Refresh. The normal job should be one contextual flow: set up, review changes, or view the approved greeting.

9. **Message creation gives advanced options equal weight.**  
   `src/features/live/LiveMessageScreen.tsx:270-551` simultaneously exposes all built-ins, language, tone, fallback style, segment cap, full editor, Gemini disclosure/actions, suggestions, and preview. AI assistance and advanced delivery options should be opt-in disclosures.

10. **Activity and Attention expose support detail in routine use.**  
    `src/features/live/LiveActivityScreen.tsx:234-433` uses large cards and exposes technical reason codes in details.  
    `src/features/live/LiveAttentionScreen.tsx:167-273` has a useful issue grouping, but repeats gate names such as “blocks test/activation/birthday jobs” and technical codes.

11. **Privacy is complete but structurally difficult to understand.**  
    `src/features/live/LivePrivacyScreen.tsx:460-799` presents eight unrelated operations as one radio group, then a generic review button. Review content exposes raw `privacy.consequence.*` keys and operation reason codes. Sign-out, local wipe, history clearing, contact disconnect, and account deletion are materially different jobs and should not look interchangeable.

12. **Privacy and support information are repeated.**  
    `src/features/live/LivePrivacyInventory.tsx` and `LiveCloudPrivacyBoundary.tsx` appear across Settings, Privacy, and Help/Legal.  
    `src/features/live/LiveDiagnosticsScreen.tsx` already has the correct explicit Preview → Share workflow and is the appropriate place for technical data.

13. **User-facing copy leaks implementation concepts.**  
    `src/localization/liveResources.ts` includes phrases such as “protected Claim, Arm, one-shot checks,” “protected 400-day simulation,” raw readiness contracts, and “protected activation review.” Android test-result copy also needs to match the binding distinctions:
    - Submitted: “Sending from this phone”
    - Successful send callback: “Sent from this phone; delivery not confirmed”

14. **Existing test contracts encode the current clutter.**  
    `tools/ui-accessibility-contract.test.mjs` requires the current Privacy radio group.  
    `tools/live-navigation-contract.test.mjs`, `src/app/LiveApp.test.tsx`, and the Maestro flows reference current Home/Settings buttons and route behavior. These tests must be updated intentionally, not bypassed.

# Dependency-Ordered Phases and Tasks

## Phase 0 — Lock Safety and Truth Contracts

### 0.1 Add product-behavior characterization coverage

**Locations**

- `src/app/LiveApp.test.tsx`
- `tools/ios-companion-workflow-boundary.test.mjs`
- Existing Android workflow/native boundary tests under `tools/`
- `src/localization/liveResources.test.ts`

**Steps**

1. Record the current native method order for Android test, activation, pause, approval, and iOS proposal/composer workflows.
2. Add assertions for platform suppression, immutable approval invalidation, stale nonce/revision recovery, and destructive-operation recovery.
3. Assert exactly three tabs and that Activity remains a Home-origin leaf.
4. Assert that technical reason codes remain available to Diagnostics/support even when removed from routine UI.

**Acceptance**

- No bridge/native contract changes.
- No Android path can activate without the existing test and review gates.
- No iOS path calls automatic sending.
- Android-managed or unavailable iOS states never expose composer actions.
- Existing duplicate-prevention and privacy-operation tests remain green.

## Phase 1 — Establish the Task-Leaf IA

### 1.1 Add only the missing focused routes

**Depends on:** 0.1

**Locations**

- `src/features/live/LiveAppShell.tsx`
- `src/domain/navigation/model.ts` only if native route typing needs clarification
- New `src/features/live/LiveComposerReviewScreen.tsx`

**Steps**

1. Keep the three bottom tabs unchanged.
2. Add stack leaves for `ComposerReview`, `Policy`, and, if needed for resumability, `GuidedSetup`.
3. Keep `Automation` as the platform-specific sending/reminder settings leaf.
4. Map native `automation-review` to `ComposerReview` on eligible iOS and to `Automation` on Android; preserve `attention`.
5. Retain origin-tab-before-leaf behavior so visible Back, gestures, and Android system Back agree.

**Acceptance**

- There are still exactly three tabs.
- No setup, privacy, diagnostic, or composer task becomes a tab.
- A birthday reminder opens directly on the actionable iOS review, not the bottom of Automation.
- Back from every new leaf returns to its visible origin.

## Phase 2 — Separate Configuration from Platform Actions

### 2.1 Extract the iOS composer job

**Depends on:** 1.1

**Locations**

- `src/features/live/LiveAutomationScreen.tsx`
- New `src/features/live/LiveComposerReviewScreen.tsx`
- `src/features/live/composerErrorCopy.ts`
- `src/features/live/LiveAppShell.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Move proposal, preparation, final disclosure, “Review message,” cancellation/error recovery, and terminal outcome UI into `LiveComposerReviewScreen`.
2. Preserve all existing proposal revision, reservation, nonce, CAS, and recovery calls.
3. Keep the final disclosure immediately before opening `MessageUI`.
4. Remove composer controls from ordinary iOS reminder settings.
5. Use “Apple’s message composer opens inside Birthday Autopilot”; never imply that the app knows final content, sender, transport, or delivery.

**Acceptance**

- The only primary action on a due iOS job is “Review message.”
- No composer action appears when Android manages the account or iOS companion capability is unavailable.
- Cancelling or returning from the composer does not claim a send.
- Unknown final state remains unknown and recoverable.
- A reminder deep link lands on this screen.

### 2.2 Simplify Android sending and iOS reminder settings

**Depends on:** 2.1

**Locations**

- `src/features/live/LiveAutomationScreen.tsx`
- `src/features/live/LiveAndroidDeviceControls.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Give both platform variants a compact current-status summary and one contextual primary action.
2. Android: reveal the test-number form only during initial setup, invalidation, or after “Run another test.”
3. Android: retain explicit test review, cost/SIM disclosure, activation review, pause/resume, readiness failures, and sender-transfer truth.
4. iOS: retain reminder permission, activation, pause/resume, and window summary, but move healthy horizon metrics behind support details.
5. Show blocker lists only when action is required; move raw codes and healthy scheduler internals to Diagnostics.
6. Split normal notification controls from sender transfer; show transfer only for standby/in-progress/error states.

**Acceptance**

- Android activation remains impossible without a successful current test.
- “Submitted” and “sent” copy use the binding phrases and never claim carrier delivery.
- iOS reminder activation never implies unattended sending.
- One primary action is visible per state; reviews replace the primary action while open.
- Sender/device ownership and transfer states remain explicit.

### 2.3 Make schedule editing a focused leaf

**Depends on:** 1.1

**Locations**

- `src/features/live/LivePolicyEditor.tsx`
- `src/features/live/LiveAppShell.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Present the current window as a plain-language summary before editing.
2. Keep start/end, grace, and cap controls, but reveal grace/cap detail progressively.
3. Continue running the mandatory policy simulation before save/review.
4. Show normal users a valid summary or a specific conflict repair; keep raw simulation counts in Diagnostics/support.
5. Retain explicit save/review and approval invalidation consequences.

**Acceptance**

- An invalid or unsafe schedule cannot be saved.
- The 400-day safety simulation still runs.
- The screen has at most one primary action.
- Hindi, large text, and screen-reader users can edit and understand each time field and error.

### 2.4 Simplify message editing

**Depends on:** 1.1

**Locations**

- `src/features/live/LiveMessageScreen.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Lead with the current template, language, editable message, and exact preview.
2. Put tone and Gemini generation behind “Help me write.”
3. Show the Gemini privacy disclosure only after that feature is opened.
4. Put fallback-name behavior and segment cap behind “Message options.”
5. Preserve built-in/offline templates, generic no-name fallback, exact preview, affected-approval warning, and explicit save.

**Acceptance**

- No contact PII is sent to Gemini.
- Saving a materially changed template still invalidates affected approvals.
- Suggestions are never silently saved or approved.
- The default path requires no AI interaction.
- The final preview remains visible before save.

## Phase 3 — Simplify Home and Settings

### 3.1 Turn Home into the readiness control center

**Depends on:** 2.1–2.4

**Locations**

- `src/features/live/LiveHomeScreen.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Derive a clear state hierarchy: setup incomplete, blocking attention, due iOS review, paused, healthy.
2. Show one readiness statement, the next relevant birthday, compact Today/Week counts, and a short enabled/attention summary.
3. Use one contextual primary action:
   - Continue setup
   - Fix issue
   - Review message on eligible iOS
   - Set up sending/reminders
   - Review today’s birthday ambiguity
4. Keep Activity and pause/resume as secondary controls.
5. Replace the always-visible exact message with “View message.”
6. Collapse contacts sync, scheduler heartbeat, active sender, coordination, and reminder horizon into a readiness-details route or disclosure.
7. Remove normal-state Refresh, Edit message, and Automation button piles.

**Acceptance**

- A user can answer “ready?” and “who is next?” without scrolling through system internals.
- Home still exposes Activity, Fix, master pause state/control, sender/coordination status, and contacts health through appropriate detail paths.
- Managed-by-Android iOS suppresses local composer/reminder actions.
- No safety or delivery claim becomes stronger than the native projection.

### 3.2 Rebuild Settings as a grouped hub

**Depends on:** 2.2–2.4

**Locations**

- `src/features/live/LiveSettingsScreen.tsx`
- `src/features/live/LiveAndroidDeviceControls.tsx`
- `src/features/live/LiveHelpLegalScreen.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Use grouped rows:
   - Birthday plan: Message, Schedule, Android sending or iOS reminders
   - Account and privacy: Google account, Privacy
   - Help: Help/legal, Diagnostics
2. Remove Activity and Attention entries; their replacement is Home.
3. Remove the duplicated readiness dashboard and privacy inventory.
4. Remove static “follows phone” cards from the main Settings page; retain that truth in About/help if needed.
5. Keep notification permission and Android sender transfer contextually visible.
6. Move Diagnostics under Help/support rather than presenting it as a primary setting.

**Acceptance**

- Every removed button has a documented replacement route.
- Privacy inventory appears once, under Privacy.
- Readiness issues appear once, through Home/Attention.
- Account identity and platform-specific sender/notification state remain discoverable.

## Phase 4 — Make Setup Linear and Contextual

### 4.1 Reduce identity/contacts setup to its current job

**Depends on:** 3.1–3.2

**Locations**

- `src/features/live/LiveSetupScreen.tsx`
- `src/app/NativeAppBoundary.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Show only the current welcome/account/contacts step, its necessary disclosure, and one primary action.
2. Put account compatibility/cost truth at welcome and contacts data-use truth directly before authorization.
3. Hide delivery gate dashboards until the product-setup phase.
4. Show Refresh only for a failed projection or return from system settings.
5. Preserve deletion/account-repair states as blocking recovery UI.

**Acceptance**

- Contacts are never requested before their disclosure.
- Repair/deletion states cannot be bypassed.
- Each step has one primary action and a clear Back/help path.
- No setup step claims success before the native projection confirms it.

### 4.2 Convert product setup into two real steps

**Depends on:** 4.1 and Phase 2

**Locations**

- `src/features/live/LiveProductSetupJourney.tsx`
- `src/features/live/LiveBatchApprovalScreen.tsx`
- `src/app/NativeAppBoundary.tsx`
- `src/features/live/LiveAppShell.tsx`

**Steps**

1. Replace the completed-step dashboard with:
   - Choose people and message
   - Test and enable on Android, or enable reminders on iOS
2. Reuse focused Message, Policy, People/approval, and platform settings tasks.
3. Keep “Finish later,” then show a persistent-in-state “Continue setup” primary action on Home.
4. Avoid silently advancing after external settings or native operations; reload and explain the confirmed state.
5. Show SMS permission/carrier/cost disclosure at Android test time and notification/composer disclosure at iOS reminder activation.

**Acceptance**

- Android cannot finish setup without enrollment, approvals, current test, and activation gates.
- iOS completion never claims automatic sending.
- Deferred setup leaves the user in a truthful limited state with an obvious continuation path.
- The journey does not repeat identity and contacts as large completed cards.

## Phase 5 — Streamline People and Approval Work

### 5.1 Preserve useful discovery and remove duplicate controls

**Depends on:** Phase 3

**Locations**

- `src/features/live/LivePeopleScreen.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Retain search and All/Enabled/Ready/Needs attention/Excluded filters.
2. Keep “Sync contacts” as the deliberate data refresh.
3. Remove the bottom projection Refresh; use Retry for errors and normal projection subscriptions.
4. Add a contextual “Review N approvals” banner when approvals require attention.
5. Use compact list rows rather than nested dashboard cards where possible.

**Acceptance**

- Search/filter semantics do not change.
- Contacts sync remains explicit and reports partial/error outcomes truthfully.
- Empty states distinguish no contacts, no match, no eligible people, and revoked contacts access.

### 5.2 Make person detail one contextual flow

**Depends on:** 5.1 and 2.4

**Locations**

- `src/features/live/LivePersonDetailScreen.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Choose the main job from state:
   - Set up greeting
   - Review changes
   - View approved greeting
2. Show phone/date/leap-day choices only when ambiguous or after “Change.”
3. Sequence resolution → explicit enrollment → exact platform-correct approval.
4. Move Pause, Exclude, and Block number under “Manage this person,” with distinct consequence copy.
5. Remove the repeated generic platform banner and routine Refresh.

**Acceptance**

- Enrollment and approval remain separate explicit consents even when presented in one guided flow.
- Approval records remain per recipient and immutable.
- Phone, birthday, message, schedule, cap, or platform changes invalidate approval exactly as before.
- Pause, Exclude, and Block cannot be confused with one another.

### 5.3 Make bulk enrollment counts truthful

**Depends on:** 5.1–5.2

**Locations**

- `src/features/live/LivePeopleScreen.tsx`
- `src/features/live/LiveBatchApprovalScreen.tsx`
- `src/features/live/LiveAppPort.ts` only if a read-only preview cannot be derived safely

**Steps**

1. Scan or request the exact eligible candidates before showing the review count.
2. Show the recipient list/count and exact message policy in the explicit batch review.
3. Create individual enrollment/approval records through existing safe operations.
4. Stop and report partial completion without presenting unprocessed people as approved.

**Acceptance**

- Overall contact count is never presented as ready-candidate count.
- A user knows exactly who will be enrolled before confirmation.
- Partial batches identify completed, skipped, and failed recipients.
- Duplicate retries cannot create duplicate approvals or sends.

## Phase 6 — Consolidate Recovery, History, Privacy, and Support

### 6.1 Make Attention the single repair list

**Depends on:** Phase 3

**Locations**

- `src/features/live/LiveAttentionScreen.tsx`
- `src/features/live/LiveProjectionState.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Retain account/contacts/approval/platform grouping.
2. Lead with the human consequence and one repair action.
3. Remove internal gate names and reason codes from the normal card.
4. Put support reference and raw reason behind an explicit “Support details” disclosure.

**Acceptance**

- Every actionable issue has one valid destination.
- Non-actionable issues honestly offer wait/retry/help without false repair.
- Screen readers do not announce support codes before the useful explanation.

### 6.2 Simplify Activity and place history clearing there

**Depends on:** Phase 3

**Locations**

- `src/features/live/LiveActivityScreen.tsx`
- `src/features/live/LiveAppShell.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Replace nested cards with compact outcome rows.
2. Keep recovery actions in Activity Detail.
3. Move technical reason codes to support details.
4. Add “Clear activity” to Activity with the exact retained-ledger/terminal-marker disclosure.
5. Keep load-more behavior and the 30-day ordinary-history limit.

**Acceptance**

- Submitted, sent-from-phone, failed, skipped, paused, and iOS unknown outcomes remain distinct.
- Clearing ordinary history does not clear the Android safety ledger or required iOS terminal marker.
- Recovery routes return to the correct origin and do not mutate status prematurely.

### 6.3 Rebuild Privacy around user-owned data jobs

**Depends on:** 3.2

**Locations**

- `src/features/live/LivePrivacyScreen.tsx`
- `src/features/live/LivePrivacyInventory.tsx`
- `src/features/live/LiveCloudPrivacyBoundary.tsx`
- `src/localization/liveResources.ts`

**Steps**

1. Replace the eight-item radio group with grouped rows:
   - Data on this phone
   - Contacts and Google
   - Sign out
   - Erase local app data
   - Delete app account
2. Make Sign out a flow that next asks whether to keep or wipe local setup.
3. Isolate account deletion as the danger action.
4. Keep `prepareAction` → consequence review → `confirmAction`, preissued permits, offline restrictions, remote-unknown/draining recovery, and external-copy behavior unchanged.
5. Never display `privacy.consequence.*` keys.
6. Move Clear activity to Activity and saved Gemini-template deletion to Message, while retaining deep links if the privacy hub must remain the authoritative entry.
7. Show the data inventory and cloud boundary only here.

**Acceptance**

- Each action has action-specific review copy and a focused primary confirmation.
- Account deletion cannot be mistaken for sign-out or local wipe.
- In-progress or unknown deletion suppresses conflicting actions.
- Pending external copies and offline limitations remain truthful and recoverable.
- Screen-capture/privacy information remains discoverable.

### 6.4 Consolidate Help and Diagnostics

**Depends on:** 6.1–6.3

**Locations**

- `src/features/live/LiveHelpLegalScreen.tsx`
- `src/features/live/LiveDiagnosticsScreen.tsx`
- `src/features/live/LiveProjectionState.tsx`

**Steps**

1. Keep Help, legal links, build/edition, and required external deletion URLs.
2. Add Diagnostics as a support row.
3. Remove the repeated cloud/privacy inventory content.
4. Keep Diagnostics’ explicit Preview → Share interaction and PII-safe payload.
5. Allow technical reason codes and support references in the preview.

**Acceptance**

- Diagnostics cannot share before preview.
- Export remains PII-safe.
- Legal and external deletion routes remain available.
- Technical detail is discoverable without dominating ordinary screens.

## Phase 7 — Copy, Accessibility, and Release Audit

### 7.1 Replace internal copy and maintain localization parity

**Depends on:** All UI phases

**Locations**

- `src/localization/liveResources.ts`
- `src/localization/liveResources.test.ts`
- `src/localization/reasonCopy.ts`
- `src/features/live/composerErrorCopy.ts`

**Steps**

1. Replace “protected,” Claim/Arm, CAS, one-shot, horizon, coexistence, contract keys, and gate-code language with user consequences.
2. Update English and Hindi together.
3. Preserve exact platform distinctions and bidi isolation for user/contact/device values.
4. Keep reason-code mappings for Diagnostics/support.

**Acceptance**

- English/Hindi key parity passes.
- Pseudo-RTL and interpolation isolation pass.
- Routine screens expose no raw contract key or technical code.
- Android and iOS outcome language matches the binding platform truth.

### 7.2 Update accessibility and navigation contracts

**Depends on:** 7.1

**Locations**

- `tools/ui-accessibility-contract.test.mjs`
- `tools/live-navigation-contract.test.mjs`
- `tools/screen-crosswalk.test.mjs`
- `src/design-system/components/Primitives.tsx`
- `src/app/LiveApp.test.tsx`
- `stitch/SCREEN_MANIFEST.md` only for the route/file crosswalk

**Steps**

1. Replace the Privacy `SingleChoiceGroup` assertion with grouped-row and focused-review assertions.
2. Assert one primary action per stateful screen.
3. Cover route focus, Back semantics, 48dp targets, 200% text, screen readers, high contrast, reduced motion, Hindi, and pseudo-RTL.
4. Update the screen manifest only for newly introduced production routes/files.

**Acceptance**

- No horizontal clipping or hidden primary action at 200% text.
- Reviews receive accessibility focus and are announced before confirmation.
- All controls have roles, states, and meaningful localized labels.
- Three-tab and stack-leaf contracts pass.

# Testing Strategy

Run focused tests after each slice:

```sh
npm test -- src/app/LiveApp.test.tsx
npm test -- src/localization/liveResources.test.ts
npm test -- src/design-system/components/Primitives.test.tsx
npm run test:tools
npm run typecheck
```

Before handoff:

```sh
npm run check
npm run android:test
npm run e2e:android
npm run e2e:ios
npm run e2e:android:large-text
npm run e2e:ios:large-text
npm run smoke:android
npm run smoke:ios
```

Update and run:

- `e2e/maestro/01-fresh-setup.yaml`
- `e2e/maestro/02-navigation-help-dark-privacy.yaml`
- `e2e/maestro/03-hindi-localization.yaml`
- `e2e/maestro/04-large-text-primary-action.yaml`
- `e2e/maestro-production-smoke/01-production-navigation.yaml`

The behavior matrix should cover Android eligible/unavailable devices, permission denial, multiple SIMs, active/standby sender transfer, test submitted/sent/failed, offline activation, approval invalidation, and duplicate callbacks. iOS coverage should include managed/unavailable suppression, notification denial, stale proposals, composer cancellation, unknown final outcomes, and partial reminder horizons. Privacy coverage should include offline deletion, remote-unknown state, external copies, retry, and retained safety history.

# Risks

- Moving iOS composer orchestration could accidentally change nonce/reservation sequencing. Extract the existing workflow without rewriting its native calls, and lock it with characterization tests first.
- Hiding healthy-state detail may conceal a genuine blocker. Only collapse verified healthy details; all blockers must remain visible on Home and Attention.
- Bulk preview can drift while contacts sync. Confirmation must bind the reviewed candidate/version set and report stale or partial results.
- Setup deferral may become confusing across restarts because it is currently in-memory. Home must truthfully re-offer setup until native state confirms completion.
- New leaves can break deep-link and Back behavior. Route-origin tests are required before removing old entry points.
- Copy simplification can accidentally overstate delivery or iOS behavior. Platform-truth assertions should use exact required phrases.
- **Self-critique:** Splitting configuration into focused leaves improves comprehension but increases navigation depth. The guided setup should orchestrate those leaves linearly and return users to the next unfinished step.
- **Self-critique:** Moving technical data to support details assumes most users prefer less telemetry. Advanced users may still need it, so Diagnostics and expandable support references must remain easy to find.
- **Self-critique:** A state-derived Home primary action can oscillate as projections refresh. Priority ordering and stable transition/loading states must be explicit to avoid button churn.

# Rollback Plan

- Implement phases as separate, reviewable commits with no storage migration or native contract change.
- Keep the existing Automation orchestration intact until the extracted composer and schedule routes pass parity tests.
- Switch entry points only after their replacement route is tested; do not delete the former UI block in the same initial extraction commit.
- If a slice fails, restore its previous route/presentation while retaining new characterization tests.
- Never roll back by bypassing approval, test, claim/arm, privacy confirmation, deletion recovery, or platform suppression.
- Because data models and native operations remain unchanged, rollback should require only React Native UI, localization, and test restoration.

# Edge Cases

- Setup deferred, app killed, then relaunched before activation.
- Zero contacts, no eligible contacts, search with no match, or revoked contacts permission.
- Contact phone/birthday changes during person or batch review.
- Leap-day birthdays, “birthday today” ambiguity, timezone changes, and DST window boundaries.
- Message, schedule, segment cap, device, or phone changes invalidating approval.
- Android multiple-SIM selection, missing telephony capability, cost consent, standby sender, and interrupted transfer.
- Permission denied, denied permanently, or changed in system settings.
- Native callback duplicated, delayed, stale, or received after navigation away.
- iOS managed by Android, companion unavailable, notification horizon partial, proposal stale, composer cancelled, and final outcome unknown.
- Bulk processing succeeds for only part of the reviewed set.
- Activity cleared while safety ledger or terminal markers must remain.
- Account deletion offline, draining, remote-unknown, retrying, or leaving external copies.
- Hindi expansion, pseudo-RTL interpolation, screen reader focus, high contrast, reduced motion, and 200% text.
- Projection refresh changes the contextual primary action while a review is open.

# Open Questions

1. Should “Finish later” persist across launches? Recommended: persist only a presentation preference if an existing safe native projection supports it; never mark setup complete. Home should continue showing “Continue setup.”
2. For a healthy Home with no urgent job, should there be no filled primary action or “View next greeting”? Recommended: avoid manufacturing urgency; use a secondary “View next greeting” row.
3. Should bulk review cover the current filtered result or only the loaded page? Recommended: filtered result only if the exact stable candidate set can be previewed; otherwise label and scope it explicitly to the current page.
4. What does the current native “clear AI templates” operation actually delete? Its label should name only persisted Gemini-created content and must not imply that transient suggestions are stored.
5. Can the existing design/native stack provide an accessible time picker without adding a dependency? If not, retain validated text fields for this simplification rather than broadening scope.
6. Confirm the exact user-facing retention wording for the Android safety ledger and iOS terminal marker against native behavior before moving “Clear activity” into Activity.
