# 04 — Experience architecture and user journeys

## 1. Experience principles

- Show the next useful action, not decorative metrics.
- Keep birthday date and relationship context visible where decisions are made.
- Use progressive disclosure for advanced personalization and technical settings.
- Keep the message itself dominant in Message Studio.
- Preserve draft content when an operation fails or the user navigates away.
- Never disguise a permission, entitlement, provider or external-app failure as success.
- Use consistent status language across dashboard, calendar, studio and history.
- Support narrow Android devices, keyboard insets, large text, screen readers and reduced motion.

## 2. Information architecture

Primary destinations:
1. Dashboard — today, action needed and upcoming birthdays.
2. People — recipient directory, add/edit, import/export.
3. Calendar — month/day birthday view.
4. History — drafts, handoffs and user-confirmed outcomes.
5. Settings — account, subscription, AI, backup, reminders, privacy and help.

Contextual destinations:
- Onboarding.
- Add/edit person.
- Message Studio for an occurrence or person.
- Permission education.
- Subscription purchase/restore.
- Credential setup/test/remove.
- Backup/restore.
- Export/deletion confirmation.
- Support/legal pages.

This is the target information architecture. Route presence in source is not proof that every journey is complete.

## 3. Role and permission model

| Actor | Allowed actions | Restrictions |
|---|---|---|
| Local app user | Create/edit/delete own people, prepare drafts, configure reminders, initiate handoff | Cannot bypass AI entitlement; cannot infer external send status |
| Authenticated account owner | Above plus opt-in backup/restore, subscription status and account lifecycle | Only own UID-scoped records; cloud actions require valid session |
| Android OS | Grant/deny permissions, schedule alarms, deliver notifications, resolve intents | App must handle revocation and unsupported device state |
| AI provider | Return generated text for a request | No independent access to app database; no authority to send or confirm |
| External messaging app | Receive a prefilled handoff | App cannot assume user sent or recipient received it |
| Support/operator | Handle defined support/deletion operations | No access to message bodies or private contact details by default |

Admin roles for a multi-tenant web console are not in core scope. Any operational admin surface requires a separate role model and approval.

## 4. Core journeys

### JRN-001 — First launch and first value
**Start:** no onboarding completion record.  
**Flow:** initialize local storage → explain the product and local-first boundary → explain reminders and optional AI/cloud capabilities → ask for only necessary permission at point of use → offer add first person → save first person → show next birthday.  
**Alternatives:** skip optional explanation; deny contacts/notification permission; close onboarding; restore existing local state.  
**Failure:** initialization failure must block writes and show recoverable guidance; never silently start with an in-memory-only store.  
**Complete when:** user can see the first saved person and upcoming birthday.

### JRN-002 — Add or edit a person
**Start:** People or Dashboard.  
**Flow:** open form → enter required name and birthday month/day → optionally add year, phone, relationship, tone, language, notes and facts → validate → save person and related occurrence consistently → refresh dashboard/calendar.  
**Alternatives:** CSV import; device contacts import with explicit permission; cancel with unsaved-change confirmation.  
**Failure:** preserve form values; explain duplicate candidates; do not silently coerce invalid dates or enum values.  
**Complete when:** the person and current occurrence are persisted and visible.

### JRN-003 — Upcoming birthday and reminder
**Start:** app foreground or OS notification.  
**Flow:** compute occurrence under one date policy → compute configured offsets and quiet hours → schedule native notification → user opens notification → resolve person/occurrence → open relevant studio or detail.  
**Alternatives:** notification permission denied; exact alarm permission unavailable; quiet hours defer/suppress per approved policy; user changes timezone or date.  
**Failure:** report reminder unavailable and provide settings action; never imply a notification is scheduled if OS rejected it.  
**Complete when:** user reaches the correct occurrence context.

### JRN-004 — Prepare manually
**Start:** dashboard, people, calendar or history.  
**Flow:** open studio → show recipient and birthday context → enter/edit message → autosave with revision → review → select channel.  
**Failure:** save failure preserves local editor state and exposes retry; stale writes cannot overwrite a newer revision.  
**Complete when:** a recoverable draft exists.

### JRN-005 — Prepare with AI
**Start:** studio with a draft or empty editor.  
**Preconditions:** active application entitlement; selected provider is configured and available.  
**Flow:** user chooses generate/rewrite → app validates entitlement → resolve configured provider → assemble prompt from approved facts → run durable job → validate response → save new draft revision → show editable result and provider/error status.  
**Alternatives:** no entitlement → explain subscription gate and preserve manual editing; no provider → explain setup/availability; invalid BYOK key → explicit repair; Nano unavailable → do not pretend it ran.  
**Failure:** timeout, cancellation, quota, malformed output and stale response preserve prior valid draft.  
**Complete when:** user receives an editable generated revision or a truthful recoverable error.

### JRN-006 — WhatsApp handoff and confirmation
**Start:** user has reviewed a draft and chooses WhatsApp.  
**Flow:** validate destination → build official click-to-chat URI → user taps handoff → app launches external WhatsApp → app records handoff attempt only → user personally taps Send → user returns → app asks whether they sent → affirmative response records user-confirmed status; negative/unknown leaves outcome unconfirmed.  
**Failure:** launch failure is not a handoff; app unavailable offers copy/share fallback; no automatic send retry.  
**Complete when:** user-confirmed status is recorded, not when WhatsApp opens.

### JRN-007 — Cloud backup and restore
**Start:** user explicitly enters backup settings.  
**Flow:** explain data categories and privacy boundary → authenticate → opt in → validate account and remote state → create immutable snapshot generation → verify upload → on restore, validate complete manifest and every record before local mutation → show outcome.  
**Alternatives:** offline, token expired, partial upload, corrupt record, newer local edits, account mismatch.  
**Failure:** no partial destructive restore; show skipped/error counts and recovery action.  
**Complete when:** the user sees a verified backup/restore result.

### JRN-008 — Subscription and AI entitlement
**Start:** user chooses AI while locked or opens subscription settings.  
**Flow:** explain what the subscription unlocks → launch supported purchase flow → obtain purchase token → verify server-side with Google Play → persist server-authoritative entitlement → client refreshes state → AI becomes available only after verified active entitlement.  
**Alternatives:** cancel, pending, expired, revoked, network outage, verification unavailable, restore purchase.  
**Failure:** fail closed for AI; do not grant from local flags or token presence. Manual drafting remains available.  
**Complete when:** server-authoritative entitlement is active and reflected by the client.

### JRN-009 — Export, account deletion and local data cleanup
**Start:** user opens privacy/data controls.  
**Flow:** explain local data, cloud backup, credentials and account scope separately → export or request deletion → require recent authentication for account-level deletion → create request receipt → process deletion saga → verify completion → explain remaining local-only data and user action needed.  
**Failure:** distinguish request accepted, processing, completed and unknown; do not claim deletion based on request acceptance.  
**Complete when:** each data location has a verified disposition.

## 5. Screen contracts

| Screen | Must answer | Required states |
|---|---|---|
| Onboarding | What does this app do and what is optional? | first run, skipped, resumed, storage failure |
| Dashboard | Who needs attention today? | loading, no people, today, upcoming, overdue/action-needed, error |
| People | Who have I saved? | empty, search result, no match, import preview, permission denied, error |
| Person form | What information is needed to remember this person? | create, edit, validation, saving, conflict, discard |
| Calendar | Which birthdays occur on this date/month? | month, no events, multiple events, date navigation |
| Message Studio | What message am I preparing and what can I do next? | empty, draft, generating, generated, locked, unavailable, failed, handing off, confirmed |
| History | What happened and what is actually known? | draft, handoff, user-confirmed, failed/unknown, empty |
| Settings | Which capabilities and preferences can I control? | signed out/in, entitlement states, provider states, permission states, backup states |
| Backup/restore | What data will leave or enter this device? | opt-in, validating, uploading, restoring, partial failure, verified |
| Privacy/deletion | What data exists and how can it be removed? | local-only, cloud-backed, request accepted, processing, completed, unknown |

## 6. Interaction and accessibility requirements

- Every form identifies required and optional fields before submission.
- Validation is field-specific and does not erase valid input.
- Save and generate controls expose in-progress and disabled states.
- Keyboard-visible controls remain reachable when the Android IME opens.
- Every icon-only control has an accessible label and state.
- Status is communicated by text/icon semantics, not color alone.
- Touch targets meet the approved Android accessibility baseline.
- Large font scaling does not clip critical actions or hide confirmation controls.
- Reduced-motion preferences are respected.
- Confirmation dialogs describe the irreversible consequence, not just a generic "Are you sure?"
- Destructive actions offer undo only when the underlying data can truly be restored.
- External handoff screens state that the user must complete sending in the external app.

## 7. Content language and status vocabulary

Canonical labels:
- Draft
- Ready to review
- AI locked (subscription required)
- AI unavailable
- Opened in WhatsApp
- Opened in SMS app
- Shared
- Copied
- User confirmed sent
- Failed to open
- Outcome not confirmed

Do not use "Delivered" unless the channel provides verified delivery evidence. Do not use "Sent" for an app-launch result.
