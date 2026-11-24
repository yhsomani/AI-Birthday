# 06 — User Experience, Journeys and Design-System Contracts

**ID:** BP-UX-001 · **Version:** 1.0 · **Status:** Target UX specification.

## 1. Experience principles

1. The next useful action is primary; decorative dashboard metrics are secondary.
2. Recipient, occurrence and exact destination remain visible at consequential actions.
3. Local/manual paths remain usable without login, AI or cloud.
4. Progressive disclosure keeps technical capability/configuration out of routine flows.
5. “Opened”, “copied”, “shared”, “user-confirmed sent” and “delivered” are distinct, truthful terms.
6. Any failure preserves valid user input and states a recoverable next action.
7. Text/icon states carry meaning independent of color.
8. Narrow screen, large text, keyboard, TalkBack, focus order and reduced motion are completion requirements.

## 2. Information architecture

Primary shell: **Dashboard, People, Calendar, History, Settings**. Contextual: Onboarding; Person detail/form; import review; Studio; permission education; subscription; provider/key; backup/restore; privacy/export/deletion; support/legal.

Navigation rules:
- Deep links resolve to an entity or safe explanation; deleted/not-found entities do not crash or expose another account.
- Back never silently discards valid edits without a decision.
- Reopening a draft restores the latest committed revision.
- Settings labels show capability, permission, entitlement and connectivity as independent states.
- Search/filter state is visible, resettable and announced for assistive technology.

## 3. Role and permission UX

| Actor | Visible permissions | Restriction |
|---|---|---|
| Local user | people, calendar, reminders, drafts, local delivery/history/export | cannot grant AI entitlement or infer external send |
| Authenticated owner | above plus backup/restore, subscription, cloud deletion | only own account data; recent auth for sensitive action |
| OS | permission/capability prompts | app explains denial and supports manual path |
| Operator (future) | separately authorized support/release/deletion tooling | no private message/person content by default |

## 4. Journey catalogue

### JRN-001 First launch to first value
**Start:** first launch/no completed onboarding. **Actions:** show concise purpose/privacy boundary → initialize local store → explain optional login/AI/backup → request no permissions yet → add first person or import → validate/save Person+occurrence → show next birthday/action. **Alternatives:** skip setup; sign in later; deny future permission; restore an approved backup; app update/reopen. **Failures:** local storage/migration failure blocks deceptive “saved”; offer diagnostics/export/recovery. **Complete:** person and correct upcoming occurrence are visible.

### JRN-002 Add/edit Person
**Start:** Dashboard/People. **Actions:** enter required name and month/day; optional profile context; select leap-day policy if applicable; set timezone/channel/language/AI consent; save atomically; refresh readers. **Validation:** field specific, date valid, unknown legacy enum not overwritten, E.164 policy if phone entered, sensitive facts clearly marked. **Alternatives:** cancel; discard confirmation; duplicate candidate review; repair legacy no-birthday record. **Failure:** preserve fields and distinguish validation, transaction and permission errors. **Complete:** one canonical Person and occurrence persist.

### JRN-003 Import contacts/CSV
**Start:** People → Import. **Actions:** choose source; contacts permission only on selection; parse/normalize; present source rows and field mapping; validate; show duplicates/reasons; select valid rows; user confirms; transactional save. **States:** no rows, malformed file, unsupported field, partial valid, permission denied, duplicates, cancelled, save partial failure. **Complete:** result shows imported/skipped/failed counts and navigable saved records; no auto-merge by name.

### JRN-004 Dashboard and planning
**Start:** shell. **Actions:** load canonical occurrences; group today/action-needed/upcoming; prioritize incomplete/failed actions; open relevant Studio; navigate Calendar. **Decision:** user can filter by status/relationship/time. **Failure:** stale/offline/migration state explained; loading skeleton not fake zero. **Complete:** user identifies and enters next action or intentionally leaves.

### JRN-005 Calendar
**Start:** Calendar tab or date deep link. **Actions:** navigate month; select date; review all occurrences; open action. **Alternatives:** no birthdays; multiple people; leap/day/timezone boundary; large text. **Failure:** stale/unknown occurrence does not silently invent a date. **Complete:** selected date and records agree with Dashboard and reminder model.

### JRN-006 Reminder creation and response
**Start:** preference setup, occurrence reconciliation or OS notification. **Actions:** save offsets/quiet hours → validate permission/capability → derive/reconcile schedules → register/cancel → tap notification → open exact Person/occurrence. **Alternatives:** permission denied/revoked; exact alarm unsupported; quiet-hours suppression; reboot/timezone change; disabled by user. **Failure:** UI says unavailable, not scheduled; no duplicate. **Complete:** intended action is reached and schedule state is truthful.

### JRN-007 Manual Studio
**Start:** occurrence/Person/History. **Actions:** show identity/date; open latest draft; type; autosave revision with state; review; select channel. **Alternatives:** blank draft; restore stale version; long body; external keyboard; concurrent job result. **Failure:** never overwrite newer edits; retain in-memory editor, retry save and report unsaved state. **Complete:** recoverable reviewed draft revision exists.

### JRN-008 AI-assisted Studio
**Start:** manual Studio → generate/rewrite/translate. **Actions:** request operation → entitlement check → provider/capability resolution → consentful request → progress/cancel → validate candidate → save new revision → edit/review. **Alternatives:** locked, unavailable, invalid key, quota, timeout, stale result, cancellation, model download. **Failure:** prior draft preserved; clear setup/retry/manual path; no secret/output leaked. **Complete:** editable candidate or truthful recoverable failure.

### JRN-009 WhatsApp/SMS/share handoff
**Start:** reviewed revision. **Actions:** show exact recipient/destination/body; validate; user triggers channel; record launch action/result; external app opens; return prompts “Did you send it?”; record explicit yes/no; unknown stays unknown. **Alternatives:** no number/app; URL rejection; copy/share fallback; app killed before return. **Failures:** launch failure is no handoff; opening never becomes sent. **Complete:** outcome is user-confirmed or honestly unknown/not sent.

### JRN-010 Subscription and provider setup
**Start:** locked AI or Settings. **Actions:** explain subscription vs provider; purchase/restore or configure key/Nano; validate server/store/provider separately; show state. **Alternatives:** pending, cancel, expired, refund, account mismatch, offline, invalid provider, restore. **Failure:** fail closed for AI while manual remains. **Complete:** server-authoritative entitlement and provider readiness shown as separate facts.

### JRN-011 Backup and restore
**Start:** Settings. **Actions:** explain data/privacy/security; auth; opt-in; create/verify generation; on restore select verified generation; show data/overwrite/conflict impact; full validation; transaction/merge; result report. **Alternatives:** offline, signed out, corrupt/partial, schema mismatch, newer local, account mismatch, deletion fence. **Failure:** no partial destructive restore; safe rollback/retry. **Complete:** result is verified with counts and remaining conflicts.

### JRN-012 Account/privacy/export/deletion
**Start:** Settings. **Actions:** explain local/cloud/secret scopes; export local data → or sign out → or request account deletion with recent auth and receipt; track accepted/processing/completed/failed/unknown. **Alternatives:** offline; cannot authenticate; retain local data; revoke backup consent. **Failure:** acceptance is never rendered as completion. **Complete:** each location’s verified disposition is visible.

### JRN-013 Support and diagnostics
**Start:** Settings/error. **Actions:** choose issue; offer local diagnostics and safe logs; redact by schema; show data to be attached; user submits only through approved channel; receipt. **Alternatives:** no account/network; copy report; public help. **Failure:** no silent attachment of personal content. **Complete:** user receives next step/reference or explicit offline alternative.

## 5. Screen contracts

| Screen | Required controls | Required states |
|---|---|---|
| Dashboard | today/action/upcoming groups; primary action; date; state | loading, empty, offline, stale, error, no-action |
| People | search, filter, add, import/export; row action | loading, empty, no match, partial import, deletion undo, error |
| Person form | required markers, date, relationship, facts, timezone, policies, save/cancel | create/edit/repair, dirty, validation, duplicate, saving, conflict, failed |
| Calendar | month/date navigation, occurrence list, jump to today | zero/multiple, leap, boundary, offline/stale, loading |
| Studio | editor, revision status, operations, recipient card, channel controls | draft/generating/locked/unavailable/error/review/handoff/unsaved |
| History | filters, event timeline, exact evidence, open occurrence | draft/handoff/confirmed/not-sent/unknown/failed/empty |
| Settings | sections for account, entitlement, provider, reminders, backup, privacy/help | signed out/in, checking, locked, active, expiry, permission denied, error |
| Import review | source row, mapped fields, reasons, select/repair, confirm count | invalid, duplicate, unselected, processing, partial result |
| Backup/restore | categories, account, generation, integrity, conflict impact, confirm | disabled, opt-in, upload, validate, restore, partial/failure/verified |
| Deletion | scope checklist, recent auth, receipt/status | accepted, processing, completed, failed, unknown; no misleading success |

## 6. Design system requirements

- Maintain one design-token source for semantic color, typography, spacing, shape, motion, elevation, state and touch sizing.
- Use fonts/assets with redistribution evidence; do not create conflicting palettes or private component duplicates.
- Semantic states: neutral, selected, info, success, warning, error, destructive, locked, unavailable, offline, stale and generating.
- Verify contrast for light/dark semantic pairs and opacity; brand color cannot be the only text-state mechanism.
- Minimum supported width and target touch size are measured against launch device matrix; 48dp is the current working baseline from existing design direction.
- Responsive rules prioritize scroll-safe actions, flexible headers and keyboard-visible controls. Critical actions cannot depend on fixed-height sheets.
- Focus order, labels, headings, role/state, live-region behavior and haptic feedback remain consistent.
- Motion is restrained and reduced-motion safe. No confetti/busy animation for deletion, entitlement or send truth.
- Date formats and locale direction follow approved language/region settings; accessible names include full dates where abbreviated visually.

## 7. Content and error language

Canonical state terms: `Draft`, `Ready to review`, `Opened in WhatsApp`, `Opened in SMS app`, `Copied`, `Shared`, `User confirmed sent`, `User confirmed not sent`, `Outcome not confirmed`, `AI locked: subscription required`, `AI unavailable`, `Failed to open`.

Never use `Sent` merely after launch. `Delivered` requires actual authoritative channel evidence, otherwise it is prohibited. Errors follow: what happened, whether user work is safe, safe next action, optional reference/operation ID. Internal stack traces and provider internals are excluded.

## 8. Responsive and accessibility acceptance

- All core journeys pass narrow-screen and approved large-text matrices without clipped primary/destructive actions.
- TalkBack can identify recipient, date, state, selected controls, validation and result.
- Form errors are associated with fields and do not erase valid values.
- Modal/sheet focus is trapped/returned and content scrolls with keyboard.
- Notification, chip, chart and list status is text/semantic, not color only.
- User can complete core manual journey without permissions except those needed for the selected optional action.
- Static, widget and physical-device accessibility checks are all required; one layer is not proof of the others.
