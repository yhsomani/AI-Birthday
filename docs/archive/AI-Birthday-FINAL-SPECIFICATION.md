# AI-Birthday — FINAL SPECIFICATION

> Combined convenience document. `SSOT.md` remains authoritative; this file concatenates the complete documentation set for easy reading/search.


---

# SOURCE DOCUMENT: `SSOT.md`

# AI-Birthday — Single Source of Truth

**Version:** 1.0.0  
**Status:** Final product and architecture direction  
**Application:** Flutter / Dart  
**Android native layer:** Kotlin  
**Primary AI:** User-provided Gemini API credential  
**Secondary AI:** Gemini Nano through Android AICore / ML Kit GenAI  
**Primary personal messaging channel:** WhatsApp Click-to-Chat handoff

> This SSOT defines the desired product. Existing code, audits, PRDs, architecture files, and status labels are implementation evidence only and do not override this document.

## 1. Product vision

AI-Birthday is a personal birthday assistant that remembers birthdays, prepares meaningful personalized messages, lets the user review them, and helps the user send them through the user's own communication channels.

Core lifecycle:

```text
People → Birthday → Reminder → Prepare → AI/Manual → Review → Send → History
```

AI is an enabling capability, not the product itself.

## 2. Product principles

1. User remains in control of what is sent.
2. AI creates drafts; it never silently sends a message.
3. AI must never invent recipient-specific facts.
4. Product entitlement, AI provider, AI capability, and delivery channel are separate concepts.
5. Prefer local processing where it provides a good user experience.
6. Do not silently consume an application-owned Gemini cloud quota.
7. Technical complexity must stay out of normal UX.
8. Flutter owns the product; Kotlin owns Android-specific platform integration.
9. Secrets are never logged or synchronized.
10. Offline operation should remain useful.
11. Accessibility is part of feature completion.
12. Every important behavior must be testable.

## 3. Final technology stack

### Application
- Flutter
- Dart
- Material 3 foundation
- Riverpod
- go_router
- Drift / SQLite
- flutter_secure_storage
- platform interfaces for Android-native capabilities

### Android
- Kotlin
- Android SDK
- ML Kit GenAI Prompt API / AICore for Gemini Nano
- native adapters only where Flutter plugins are insufficient

### Backend
Use the existing Firebase-oriented ecosystem where needed:
- Firebase Authentication
- Firestore
- Cloud Functions for server-side verification/reconciliation
- Google Play Billing on Android with backend entitlement verification

The backend is not a default Gemini inference proxy.

## 4. Target architecture

```text
Flutter / Dart
 ├── Presentation
 ├── Application
 ├── Domain
 └── Data
       ├── Drift / SQLite
       ├── Secure storage
       ├── Firebase repositories
       └── Platform adapters
              │
              ▼
       Kotlin Android layer
          ├── AICore / Gemini Nano
          ├── Contacts
          ├── Notifications
          └── Android-specific capabilities
```

### AI

```text
AiRouter
 ├── UserGeminiApiProvider
 └── GeminiNanoProvider
```

### Delivery

```text
DeliveryService
 ├── WhatsAppHandoff
 ├── Sms
 ├── Clipboard
 └── Share
```

## 5. Final AI routing rule

Application subscription is checked first.

```text
AI request
  ↓
Active application entitlement?
  ├── NO  → AI_LOCKED
  └── YES
       ↓
User Gemini API credential configured?
  ├── YES → UserGeminiApiProvider
  └── NO
       ↓
Gemini Nano/AICore available?
  ├── YES → GeminiNanoProvider
  └── NO  → AI_UNAVAILABLE
```

There is no automatic application-owned cloud Gemini fallback.

### Invalid credential

A configured-but-invalid credential is not treated as missing.

Show:
- connection error
- retry
- replace credential
- explicit option to use Nano when available

### User Gemini API credential

The key/credential:
- is optional
- is stored locally using secure storage
- is never logged
- is never synced
- is never sent to the backend by default
- can be tested, replaced, and removed

The UI should make clear that this is the user's own Gemini API credential and that its API-project limits/billing are determined by the credential/project configuration.

### Gemini Nano

Implement Gemini Nano for real through Android AICore/ML Kit GenAI APIs behind a Kotlin adapter.

Normalize native states:

```text
AVAILABLE
DOWNLOADABLE
DOWNLOADING
NOT_READY
UNAVAILABLE
BUSY
QUOTA_EXCEEDED
ERROR
```

Do not label a deterministic template generator as Gemini Nano.

## 6. AI capabilities

P0:
- Generate birthday message
- Regenerate
- Shorten
- Expand
- Warm
- Funny
- Emotional
- Casual
- Professional
- Translate
- Generate variations
- Personalize from user-provided facts

AI must return short, message-ready text.

## 7. Recipient model

A person should support:

```text
id
name
birthdayMonth
birthdayDay
birthYear?
phoneNumber?
email?
relationship
relationshipCloseness
preferredLanguage
preferredTone
importantFacts[]
notes
preferredDeliveryChannel
timezone?
autoPrepare
autoSendPolicy
createdAt
updatedAt
version
```

Only facts supplied by the user or imported from an allowed source may be presented as factual context to AI.

## 8. Birthday lifecycle

```text
CREATED
 → UPCOMING
 → REMINDER_DUE
 → MESSAGE_NOT_PREPARED
 → MESSAGE_DRAFTED
 → MESSAGE_REVIEWED
 → READY_FOR_DELIVERY
 → HANDED_OFF / SENT
 → COMPLETED
```

Failures move to:

```text
FAILED
 → RETRYABLE / ACTION_REQUIRED
```

## 9. WhatsApp

WhatsApp is a personal-account handoff channel.

```text
Generate/manual draft
  ↓
User reviews
  ↓
Send on WhatsApp
  ↓
Create official Click-to-Chat URL
  ↓
WhatsApp opens
  ↓
Message is pre-filled
  ↓
USER TAPS SEND
  ↓
User returns to AI-Birthday
  ↓
User confirms whether sent
```

Use international phone number formatting and URL-encoded prefilled text.

State:

```text
DRAFT
→ READY
→ HANDED_OFF_TO_WHATSAPP
→ USER_CONFIRMED_SENT
```

The app must never claim that a WhatsApp message was sent merely because WhatsApp opened.

Do not:
- scrape WhatsApp
- store WhatsApp session cookies
- automate the Send button
- simulate user taps
- use unofficial personal-account APIs

Automatic preparation is allowed. Automatic personal WhatsApp sending is not part of the product.

## 10. SMS / Copy / Share

SMS is a platform-dependent Android delivery feature.

Copy and Share are always safe fallback actions.

Delivery semantics must distinguish:
- prepared
- handed off
- sent/accepted
- delivered only when actually known

## 11. Subscription

Application AI requires active entitlement.

```text
NO ENTITLEMENT → ALL AI LOCKED
ENTITLEMENT     → PROVIDER ROUTING ALLOWED
```

Android purchase path:

```text
Flutter purchase UI
 → Google Play Billing
 → purchase token
 → backend verification
 → entitlement
 → app refresh
```

Provide purchase, restore, expired, grace, and error states.

## 12. Authentication

One login:

```text
Continue with Google
 → application account
```

There is no second AI login. Gemini credential is a provider setting, not an account login.

## 13. Offline-first

Offline:
- people
- birthdays
- calendar
- existing drafts
- local settings
- Nano when available
- local reminders

May require network:
- Firestore synchronization
- subscription refresh
- Gemini API
- external cloud operations

## 14. Timezone and leap day

Timezone rule:
1. Recipient timezone if explicitly known.
2. Otherwise user's timezone.

Feb 29 default:
- Feb 28 in non-leap years.
- User may choose Mar 1.

## 15. Home

Home is an action-oriented command center.

```text
Today
Upcoming
Action needed
Quick actions
```

Each birthday shows a state such as:
- not prepared
- draft
- reviewed
- handed off
- completed
- failed

## 16. Message Studio

Required:
- editable message
- generation
- rewrite actions
- tone
- length
- translation
- save draft
- versioning
- delivery controls

AI output is never automatically final.

## 17. Notifications

Examples:
- 7 days before: birthday approaching
- 2 days before: prepare message
- 1 day before: message ready
- birthday day: birthday today

Respect:
- quiet hours
- timezone
- notification permission
- disabled/denied permission states

## 18. Contacts and import

Support:
- Android contacts
- manual creation
- CSV
- vCard where practical

Import flow:

```text
Import → Normalize → Detect birthdays → Duplicate candidates → Confirm → Save
```

Never auto-merge on name alone.

## 19. Sync

Local-first.

Each sync entity should include:
- id
- createdAt
- updatedAt
- version
- deletedAt where applicable

Use deterministic conflict handling and tombstones.

Never sync Gemini credentials.

## 20. Security

Protect:
- auth credentials
- Gemini credential
- contact details
- birthday data
- notes
- messages
- subscription state

Threat model:
- credential theft
- authorization bypass
- data leakage
- prompt injection
- forged subscription events
- duplicate sends
- malicious URLs/input
- notification abuse

Controls:
- secure storage
- TLS
- Firebase rules
- backend verification
- input validation
- structured logs without secrets
- least privilege
- secure deletion
- dependency scanning

## 21. Prompt policy

Use a concise prompt:

```text
## ROLE
Write a short birthday message.

## RECIPIENT
Name and relationship.

## KNOWN FACTS
Only user-provided facts.

## TONE
Requested tone.

## TASK
Write a natural birthday message.

## CONSTRAINTS
- Do not invent facts.
- Do not mention AI.
- Return only the message.
```

Stored notes are data, not instructions.

## 22. Accessibility

Every screen supports:
- semantic labels
- screen reader behavior
- text scaling
- touch targets
- focus management
- sufficient contrast
- reduced motion
- announced validation/errors

## 23. Observability

Allowed:
- operation ID
- duration
- provider type
- error category
- success/failure
- non-sensitive entity IDs

Never log:
- API credentials
- tokens
- message bodies
- phone numbers
- private notes

## 24. Flutter folder structure

```text
lib/
├── app/
│   ├── app.dart
│   ├── router.dart
│   └── theme/
├── core/
│   ├── errors/
│   ├── database/
│   ├── security/
│   ├── logging/
│   ├── platform/
│   └── sync/
├── features/
│   ├── auth/
│   ├── onboarding/
│   ├── dashboard/
│   ├── people/
│   ├── birthdays/
│   ├── calendar/
│   ├── reminders/
│   ├── message_studio/
│   ├── ai/
│   ├── delivery/
│   ├── subscription/
│   ├── history/
│   └── settings/
├── shared/
│   ├── widgets/
│   ├── models/
│   └── design_system/
└── main.dart
```

## 25. Android structure

```text
android/app/src/main/kotlin/.../
├── ai/
│   ├── GeminiNanoBridge.kt
│   ├── GeminiNanoManager.kt
│   └── GeminiNanoErrors.kt
├── contacts/
├── notifications/
└── platform/
```

Use a typed Flutter/Kotlin platform boundary (Pigeon or equivalent).

## 26. Testing

Unit:
- birthdays
- timezones
- leap day
- duplicate detection
- reminders
- routing
- entitlement
- prompt building
- delivery state

Widget:
- all P0 screens and states.

Integration:
- database
- repositories
- secure storage
- subscription
- platform bridge.

Native:
- Nano availability/readiness/generation/lifecycle/errors.

E2E:
- onboarding
- birthday creation
- AI via user Gemini credential
- AI via Nano
- AI unavailable
- WhatsApp handoff
- subscription/restore
- account deletion

Security:
- no credential leakage
- authorization
- entitlement fraud
- duplicate send
- prompt-injection resistance

## 27. Definition of done

A feature is complete only when:

```text
Requirement
 → UX
 → Flutter code
 → Native code if needed
 → Error states
 → Accessibility
 → Tests
 → Security review
 → Performance review
 → Documentation
```

## 28. Final decisions

| Decision | Final |
|---|---|
| Application | Flutter/Dart |
| Android native | Kotlin |
| State | Riverpod |
| Navigation | go_router |
| Local data | Drift/SQLite |
| Secrets | flutter_secure_storage |
| Auth | Google + Firebase Auth |
| Sync | Firestore |
| Android subscription | Google Play Billing + backend verification |
| AI 1 | User Gemini API credential |
| AI 2 | Gemini Nano/AICore |
| AI cloud fallback owned by app | No |
| WhatsApp | Click-to-Chat handoff |
| Automatic WhatsApp send | No |
| User review | Required |
| Offline data | Yes |

---

# SOURCE DOCUMENT: `PRD.md`

# AI-Birthday — Product Requirements Document

## Product vision
Make remembering birthdays effortless without making messages feel robotic.

## Mission
Turn “I almost forgot” into “I already prepared something that sounds like me.”

## Target users
Primary: people managing birthdays for friends, family and colleagues.  
Secondary: busy professionals who want reminders and fast personalization.  
Advanced: users comfortable with their own Gemini API credential.

## Jobs to be done
- Remind me before birthdays.
- Help me decide what to write.
- Personalize messages using facts I provide.
- Let me edit AI drafts.
- Help me send through my own account.
- Tell me when something failed.

## Feature groups

### Account
Google sign-in, session, profile, logout, account deletion.

### People
Manual add/edit/delete, contact import, relationship, notes, language, tone, timezone.

### Birthdays
Recurring birthdays, calendar, upcoming list, leap-day rules.

### Reminders
Configurable notification offsets, quiet hours, permission handling.

### AI
Application entitlement gate, optional user Gemini credential, Gemini Nano/AICore fallback, generation, rewrite, tones, length, translation, variations, personalization.

### Message Studio
Draft, edit, save, version, review, deliver.

### Delivery
WhatsApp personal handoff, SMS where supported, copy, share.

### Automation
Automatic preparation and reminders; no automated personal WhatsApp sending.

### History
Message/delivery activity timeline.

## Golden user story

As a user, when a birthday is approaching, I want AI-Birthday to remind me and prepare a personalized draft so that I can review it and send it from my own messaging account.

## Acceptance criteria

### AI
- inactive app subscription locks all AI
- configured valid Gemini credential is selected first
- absent credential causes Nano capability check
- unavailable Nano produces an explicit unavailable state
- no application-owned Gemini cloud fallback
- generated text is editable
- AI does not invent recipient facts

### WhatsApp
- accepts normalized international phone number
- opens WhatsApp via official Click-to-Chat link
- pre-fills the message
- user presses Send
- app does not claim send just because the link opened

### Subscription
- purchase
- restore
- backend verification
- entitlement refresh
- expiry/grace/error state

## Product non-goals
Generic chatbot, unofficial WhatsApp automation, WhatsApp session scraping, application-owned Gemini cloud fallback, social network, broad AI agent platform.

## Success metrics
- birthdays captured
- birthdays with prepared messages
- reviewed messages reaching delivery action
- successful WhatsApp handoffs
- reminder engagement
- AI generation success rate
- subscription lifecycle reliability

## Product guardrails
AI-Birthday assists the user. The user remains responsible for the final message and personal-account send action.

---

# SOURCE DOCUMENT: `TRD.md`

# AI-Birthday — Technical Requirements Document

## FR-001 — Authentication
Google login through the application authentication system.
Must support logout, token refresh, expired-session recovery, and user-data isolation.

## FR-002 — People
CRUD for people with birthday, relationship, optional contact data, context and preferences.

## FR-003 — Birthday engine
Annual recurrence, timezone-aware date calculation, Feb 29 policy, upcoming queries.

## FR-004 — Contact import
Read allowed contact fields, normalize, detect duplicate candidates, require confirmation for uncertain merges.

## FR-005 — Reminders
Schedule, cancel, reschedule and deduplicate notifications. Respect permission and quiet-hour settings.

## FR-006 — Application entitlement
No active entitlement means every AI capability is locked.

## FR-007 — User Gemini provider
Secure local credential storage, connection test, generation, rewrite, translation, error mapping. Credential is never synced or logged.

## FR-008 — Gemini Nano
Kotlin implementation using the current AICore/ML Kit GenAI supported path. Capability detection, readiness/download state, generation, lifecycle cleanup and normalized error mapping.

## FR-009 — AI router
Authoritative policy:
1. entitlement
2. valid user Gemini credential
3. Gemini Nano availability
4. unavailable

No application-owned cloud fallback.

## FR-010 — Prompt builder
Separate trusted instructions from untrusted recipient data. No invented facts. Short message output.

## FR-011 — Message studio
Editable draft, generation, rewrite, save, versioning, review and delivery actions.

## FR-012 — WhatsApp handoff
Generate official Click-to-Chat link using international number and URL-encoded message; open WhatsApp; track handoff; never simulate Send.

## FR-013 — SMS
Android-capable delivery adapter with correct sent/delivered semantics.

## FR-014 — Subscription
Google Play Billing on Android, backend receipt/token verification, restore, entitlement state and reconciliation.

## FR-015 — Offline-first
Local database remains usable without network. Sync queues changes when connectivity returns.

## FR-016 — Sync
Versioned records, tombstones, deterministic conflict resolution, no credential sync.

## NFR-001 — Security
Secure secret storage, TLS, least privilege, auth rules, input validation, sanitized logs, dependency scanning.

## NFR-002 — Performance
Non-blocking generation, local-first home/calendar, efficient contact import and database queries.

## NFR-003 — Accessibility
P0 screens must meet project accessibility requirements, including semantics, scaling, contrast, focus and reduced motion.

## NFR-004 — Reliability
Operations that can repeat must be idempotent where appropriate.

## NFR-005 — Observability
Use operation IDs and sanitized diagnostics; never log credentials, tokens or message content.

---

# SOURCE DOCUMENT: `APP_FLOW.md`

# AI-Birthday — Application Flow

## 1. First run

```text
Install
 → Welcome
 → Continue with Google
 → Add/import birthdays
 → Notification setup
 → Subscription state
 → AI setup
 → Home
```

Do not force users through every setting on first launch.

## 2. Add birthday

```text
Add Person
 → Name
 → Birthday
 → Relationship
 → Contact details
 → Optional context
 → Preferred channel
 → Save
```

## 3. Import contacts

```text
Import
 → Permission
 → Select source
 → Parse
 → Normalize
 → Detect birthdays
 → Duplicate candidates
 → Confirm
 → Save
```

## 4. Upcoming birthday

```text
Reminder
 → Birthday detail
 → Message status
 → Prepare / Review / Deliver
```

## 5. AI generation

```text
Generate
 → entitlement check
 → credential configured?
    YES → User Gemini API
    NO  → Nano capability
              available → Gemini Nano
              unavailable → AI unavailable
 → validate result
 → editable draft
```

## 6. AI credential

```text
Settings → AI → Gemini API
 → Enter credential
 → Secure local storage
 → Test
 → Connected / Error
```

Actions:
- replace
- remove
- test
- switch explicitly to Nano

## 7. WhatsApp

```text
Review message
 → Send on WhatsApp
 → validate number
 → construct wa.me link
 → open WhatsApp
 → prefilled text
 → user presses Send
 → return
 → user confirms sent/not sent
```

## 8. Subscription

```text
AI action
 → entitlement check
 → paywall if inactive
 → purchase
 → backend verify
 → refresh entitlement
```

Restore follows the same verification path.

## 9. Failure

### Gemini credential invalid
Show actionable error, keep draft, allow retry/replace/Nano.

### Nano unavailable
Show unavailable state and credential setup.

### WhatsApp unavailable
Offer Copy and Share.

### Network unavailable
Keep local data usable; delay sync/cloud operations.

### Send uncertain
Never assume success. Keep pending until user confirmation.

## 10. Account deletion

```text
Settings
 → Delete account
 → confirm
 → reauthenticate if required
 → cloud deletion
 → local deletion
 → secure credential wipe
 → notification cancellation
 → logout
```

---

# SOURCE DOCUMENT: `ARCHITECTURE.md`

# AI-Birthday — Architecture

## 1. Style

Feature-first Flutter architecture with clear domain boundaries.

```text
Presentation
 → Application
 → Domain
 → Data/Infrastructure
 → Platform/Backend
```

## 2. Flutter responsibilities

Flutter owns UI, navigation, state, domain rules, local storage, AI routing, subscription UX, message editing, delivery orchestration and accessibility.

## 3. Kotlin responsibilities

Kotlin owns Android-only integrations:
- AICore / Gemini Nano
- native contacts where required
- native scheduling where required
- platform capability diagnostics

## 4. AI architecture

```text
AiRouter
 ├── UserGeminiApiProvider
 └── GeminiNanoProvider
```

Routing is a domain policy, not a widget concern.

## 5. Native AI bridge

```text
Flutter
 → typed platform interface
 → Kotlin
 → ML Kit GenAI Prompt API
 → AICore
 → Gemini Nano
```

Use Pigeon or an equivalent typed interface.

## 6. Delivery

```text
DeliveryService
 ├── WhatsAppHandoff
 ├── Sms
 ├── Clipboard
 └── Share
```

## 7. Persistence

Drift/SQLite is the operational local store. Firestore is a synchronization target. User credentials never sync.

## 8. Backend

Use backend only where server authority is needed:
- entitlement verification
- account synchronization/reconciliation
- account deletion/export support
- secure operational functions

Do not create a Gemini proxy unless a documented future requirement demands it.

## 9. Domain errors

```text
AI_LOCKED
AI_CREDENTIAL_MISSING
AI_CREDENTIAL_INVALID
AI_NANO_UNAVAILABLE
AI_BUSY
AI_QUOTA_EXCEEDED
AI_TIMEOUT
AI_PROVIDER_ERROR
PERMISSION_DENIED
NETWORK_UNAVAILABLE
SYNC_CONFLICT
DELIVERY_UNAVAILABLE
DELIVERY_FAILED
```

## 10. Concurrency

Guard against:
- duplicate sends
- duplicate reminders
- concurrent imports
- concurrent draft generations
- stale sync overwrites

Use immutable operation IDs and database constraints where appropriate.

---

# SOURCE DOCUMENT: `UI_UX_DESIGN_SYSTEM.md`

# AI-Birthday — UI/UX Design System

## 1. Experience

Warm, personal, calm, trustworthy and lightweight.

Avoid:
- dense technical dashboards
- provider jargon
- exaggerated AI visuals
- decorative animation that delays work

## 2. Navigation

```text
Home
Birthdays
Calendar
History
Settings
```

## 3. Home

Show:
- today's birthdays
- upcoming birthdays
- action needed
- quick actions

Example:

```text
Today
🎂 Ananya
Message ready

Upcoming
Rahul · tomorrow

Action needed
1 message not prepared
```

## 4. Birthday card

States:
- not prepared
- draft ready
- reviewed
- handed off
- completed
- failed

## 5. Message Studio

```text
Recipient/context
 ↓
Message editor
 ↓
AI actions
 ↓
Save/review
 ↓
Delivery
```

AI actions:
- Generate
- Regenerate
- Warm
- Funny
- Emotional
- Casual
- Professional
- Shorten
- Expand
- Translate

## 6. AI status

Normal users see:
- AI ready
- Gemini connected
- On-device AI available
- AI unavailable

Advanced AI settings may show provider details.

## 7. Credential screen

Show:
- connection status
- secure-storage explanation
- masked credential after save
- test/remove/replace

Never display full saved credential.

## 8. WhatsApp

Primary CTA:
`Send on WhatsApp`

After handoff:
`WhatsApp opened. Did you send it?`

Do not say `Sent` without user confirmation.

## 9. Micro-interactions

Generate:
```text
tap → progress → result
```

Regenerate:
```text
keep current draft → generate replacement → replace only on success
```

Save:
```text
saved → subtle confirmation
```

Errors:
```text
problem → reason → action
```

## 10. Accessibility

Semantic labels, focus, scaling, contrast, screen reader announcements, keyboard/focus behavior where applicable and reduced-motion support.

## 11. Responsive behavior

Use adaptive Flutter layouts:
- compact mobile
- medium
- expanded

Cards can become list rows; bottom sheets can become side/center surfaces on large screens.

---

# SOURCE DOCUMENT: `SECURITY.md`

# AI-Birthday — Security and Privacy

## 1. Protected data

- auth credentials/tokens
- Gemini API credential
- contact information
- birthday information
- notes
- message drafts
- subscription state

## 2. Gemini credential

The user credential is high-risk secret material.

Controls:
- OS-secure storage
- never sync
- never log
- never analytics
- masked UI
- remove/replace
- minimal in-memory lifetime

Secure storage does not make a client-side credential impossible to steal from a compromised device; disclose this risk.

## 3. AI data minimization

Send only the information needed for a message. Do not upload the user's entire contact database or unrelated notes.

Gemini Nano processes prompts on-device through AICore. Cloud generation through the user's Gemini API credential transmits the request to the selected Gemini API service.

## 4. Prompt injection

Recipient notes are untrusted data. They must never override system/product instructions.

## 5. Authentication / authorization

Backend must authorize by authenticated user identity, not client-supplied user IDs.

## 6. Subscription security

- verify purchase state server-side
- make processing idempotent
- reconcile refunds/cancellation/expiry
- never trust client entitlement alone

## 7. WhatsApp

Do not:
- collect WhatsApp credentials
- collect WhatsApp session cookies
- scrape WhatsApp
- automate UI Send
- use unofficial personal-account APIs

Only use the supported Click-to-Chat/deep-link handoff.

## 8. Logging

Never log:
- API keys
- access tokens
- message bodies
- phone numbers
- private notes

Use:
- operation ID
- duration
- category
- sanitized error code

## 9. Account deletion

Delete local and remote user data, secure credentials, notification registrations and synchronization metadata.

---

# SOURCE DOCUMENT: `TESTING.md`

# AI-Birthday — Testing Strategy

## Unit tests
Birthday calculations, leap day, timezone, duplicate detection, reminder rules, AI routing, entitlement gating, credential state, prompt construction, message versioning, delivery state and retry logic.

## Widget tests
Onboarding, home, birthday list, calendar, message studio, AI settings, subscription, history, settings.

Test every important state:
- loading
- empty
- ready
- error
- locked
- unavailable
- offline
- permission denied

## Integration
- Drift
- repositories
- secure storage
- Firebase
- subscription verification
- sync
- platform bridge

## Native Android
- Nano availability
- downloadable/not-ready state
- download
- generation
- cancellation
- busy/quota errors
- lifecycle close

## E2E
1. Login
2. Add birthday
3. Import birthday
4. AI through user Gemini credential
5. AI through Nano
6. AI unavailable
7. WhatsApp handoff
8. subscription purchase
9. restore
10. account deletion

## Security
- no credential leakage
- unauthorized access denied
- forged entitlement denied
- replayed event idempotency
- duplicate send prevention
- prompt injection resistance

## Accessibility
Screen reader, text scaling, focus, contrast, reduced motion and error announcements.

## Performance
Startup, local rendering, contact import, database latency, AI latency, Nano readiness and sync performance.

## Completion
A P0 feature is not complete until its required automated tests pass and real device/browser evidence exists where applicable.

---

# SOURCE DOCUMENT: `AGENTS.md`

# AI-Birthday — Agent Rules

Read `SSOT.md` first.

## Operating sequence

```text
Understand → Inspect → Plan → Modify → Test → Verify → Security → UX → Document → Report
```

## Never do

- remove features silently
- create a second AI router
- create a second credential store
- create a second entitlement system
- create unofficial WhatsApp automation
- call deterministic templates Gemini Nano
- add application-owned Gemini fallback
- log secrets or message content
- bypass subscription gating
- mark WhatsApp sent just because it opened
- place business logic in widgets

## Flutter rules

Use feature-first structure, Riverpod, typed domain models and repository boundaries.

Platform code must stay behind platform abstractions.

## AI rules

Provider-specific details belong inside provider implementations.

The domain knows:
- generate
- rewrite
- translate
- availability
- normalized failures

## Documentation

Every architecture/behavior change must update the relevant derived document and, when authoritative behavior changes, `SSOT.md`.

## Reporting

Every implementation report includes changed files, tests, security review, UX review, known limitations and documentation changes.

---

# SOURCE DOCUMENT: `BRAIN.md`

# AI-Birthday — Product Brain

## Product mental model

The application is a birthday workflow engine with AI assistance.

## Four dimensions

```text
Product entitlement
≠ AI provider
≠ AI capability
≠ Delivery channel
```

Example:

```text
Entitlement: active
Provider: Gemini Nano
Capability: rewrite
Channel: WhatsApp
```

## User-owned Gemini credential

This is an optional advanced provider configuration.

It should not be required for onboarding.

## Gemini Nano

Nano provides the no-key path on supported Android devices through AICore. Availability must always be detected rather than assumed.

## No cloud fallback

The app intentionally does not silently spend application-owned Gemini quota.

## WhatsApp authenticity

AI-Birthday prepares the message. The user sends it through WhatsApp.

## AI truthfulness

AI must not manufacture personal memories, events or facts.

## Simplicity

User sees:
```text
Generate → Edit → Send
```

Developer handles:
```text
entitlement → provider routing → secure storage → native bridge → lifecycle
```

## Reliability

Any repeatable operation needs deterministic state/idempotency protection.

---

# SOURCE DOCUMENT: `CODE_STYLE.md`

# AI-Birthday — Code Style

## Dart
- use `dart format`
- prefer immutable state/models
- avoid `dynamic` where possible
- keep widgets small
- use typed failures
- no swallowed exceptions

## Flutter
Widgets render state and emit user intent. They do not directly call Firebase, Gemini, Drift or native APIs.

## Naming
Classes: PascalCase.  
Methods/variables: camelCase.  
Files: snake_case.dart.

## State
Explicitly model:
loading, success, empty, error, locked, unavailable, offline.

Do not overload null.

## Native
Kotlin only for platform responsibilities. Keep the bridge narrow and typed.

## Backend
Validate input, never trust client entitlement, use idempotency, sanitize logs.

## Forbidden
- hard-coded secrets
- secret logging
- duplicate provider systems
- duplicate entitlement systems
- unofficial WhatsApp automation
- business logic in UI widgets
- silent application-owned AI fallback

---

# SOURCE DOCUMENT: `README.md`

# AI-Birthday

AI-Birthday is a personal birthday assistant that remembers birthdays, prepares personalized messages and helps users send them through their own communication channels.

## Stack

- Flutter / Dart
- Kotlin for Android-native capabilities
- Riverpod
- go_router
- Drift / SQLite
- Firebase Authentication / Firestore
- Google Play Billing on Android
- User Gemini API credential
- Gemini Nano / AICore
- WhatsApp Click-to-Chat handoff

## AI

```text
App entitlement active?
  ↓
User Gemini credential configured?
  YES → Gemini API
  NO  → Gemini Nano/AICore
         unavailable → AI unavailable
```

There is no application-owned Gemini cloud fallback.

## WhatsApp

```text
Generate → Review → Send on WhatsApp → User taps Send
```

AI-Birthday does not automate personal WhatsApp sending.

## Documentation

Read `SSOT.md` before architecture or product work.

Supporting documents:
- PRD.md
- TRD.md
- ARCHITECTURE.md
- APP_FLOW.md
- UI_UX_DESIGN_SYSTEM.md
- SECURITY.md
- TESTING.md
- AGENTS.md
- BRAIN.md
- CODE_STYLE.md
- `docs/CURRENT_STATE_AND_GAPS.md`
- `docs/DECISIONS.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/FILE_STRUCTURE.md`

---

# SOURCE DOCUMENT: `DOCUMENTATION_INDEX.md`

# AI-Birthday — Final Documentation Index

## Authority
`SSOT.md` is the single source of truth.

## Product
- `PRD.md`
- `APP_FLOW.md`

## Technical
- `TRD.md`
- `ARCHITECTURE.md`
- `docs/FILE_STRUCTURE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/DECISIONS.md`

## UX
- `UI_UX_DESIGN_SYSTEM.md`

## Quality and security
- `SECURITY.md`
- `TESTING.md`

## Engineering
- `AGENTS.md`
- `BRAIN.md`
- `CODE_STYLE.md`

## Migration
- `docs/CURRENT_STATE_AND_GAPS.md`

## Final AI rule
Active app entitlement → user Gemini credential first → Gemini Nano/AICore second → AI unavailable if neither exists. No application-owned cloud fallback.

## Final WhatsApp rule
Official Click-to-Chat/deep-link handoff with prefilled text; the user presses Send. Opening WhatsApp is not proof of sending.

---

# SOURCE DOCUMENT: `docs/DECISIONS.md`

# AI-Birthday — Final Architecture Decisions

## ADR-001 — Flutter
Flutter/Dart is the primary application framework.

## ADR-002 — Kotlin
Kotlin is the Android-native implementation language.

## ADR-003 — User Gemini credential first
If a valid user Gemini API credential is configured, the app uses Gemini API through that credential.

## ADR-004 — Gemini Nano second
Without a user credential, use Gemini Nano/AICore when available.

## ADR-005 — No application-owned Gemini fallback
Do not silently use an application-owned cloud Gemini credential.

## ADR-006 — Subscription gates AI
Inactive application entitlement locks every AI feature.

## ADR-007 — WhatsApp handoff
Use official Click-to-Chat/deep-link prefill; user presses Send.

## ADR-008 — No unofficial WhatsApp automation
No scraping, session reuse, UI automation or personal-account send simulation.

## ADR-009 — Local-first data
Local database is operationally primary; cloud is synchronization.

## ADR-010 — Typed platform boundary
Use Pigeon or equivalent typed Flutter/Kotlin interface.

## ADR-011 — Review
AI output remains editable and must be reviewed before personal WhatsApp handoff.

## ADR-012 — Product focus
AI-Birthday is a birthday workflow product, not a generic chatbot.

---

# SOURCE DOCUMENT: `docs/CURRENT_STATE_AND_GAPS.md`

# AI-Birthday — Current State and Gaps

**Authority:** migration baseline only. `SSOT.md` is the desired system.

## Main finding
The existing project contains substantial implementation, but the final product needs to converge around a Flutter-first architecture and one coherent AI/delivery model.

## Baseline
| Area | Baseline | Final target |
|---|---|---|
| Birthday workflows | substantial implementation | consolidated Flutter domain |
| Message editor | substantial implementation | Message Studio |
| AI gateway | substantial/legacy | simplify unless needed |
| User Gemini credential | incomplete | secure local optional provider |
| Gemini Nano | not proven end-to-end | real Kotlin/AICore implementation |
| AI routing | divergent/partial | one `AiRouter` |
| Entitlement | existing infrastructure | one authoritative AI gate |
| WhatsApp | needs explicit product implementation | Click-to-Chat handoff |
| Automatic WhatsApp send | not target | never implement |
| Flutter architecture | target direction | feature-first Flutter + Kotlin adapters |

## P0 gaps
1. Unified AI router.
2. Real Gemini Nano implementation.
3. Secure user Gemini credential storage.
4. Central entitlement gate.
5. WhatsApp handoff semantics.
6. Unified message lifecycle.
7. Typed Flutter/Kotlin boundary.
8. AI-data minimization and privacy disclosure.

## Wrong journey to replace
Wrong:
`Reminder → AI → automatic WhatsApp send`

Correct:
`Reminder → Prepare → AI/manual → Review → WhatsApp handoff → User sends → Confirmation`

## Migration rule
Preserve useful implementation, map it to the target architecture, add tests, and remove superseded paths only after reference/dependency checks. Existing documentation does not prove implementation.

---

# SOURCE DOCUMENT: `docs/IMPLEMENTATION_PLAN.md`

# AI-Birthday — Implementation Plan

## Phase 0 — Foundation
Flutter baseline, Riverpod, go_router, design system, Drift, secure storage, domain errors, CI.

## Phase 1 — Core product
Auth, people, birthdays, calendar, reminders, manual message drafts, history.

## Phase 2 — Subscription
Google Play Billing, backend purchase verification, entitlement state, restore, paywall.

## Phase 3 — Gemini API credential
AI settings, secure credential store, validation, provider, error states and tests.

## Phase 4 — Gemini Nano
Kotlin AICore/ML Kit bridge, capability/readiness, generation, lifecycle and Flutter adapter.

## Phase 5 — AI Router
Implement entitlement → credential → Nano → unavailable policy in exactly one location.

## Phase 6 — Message Studio
Editor, regeneration, tones, translation, personalization, versions, review.

## Phase 7 — WhatsApp
Number normalization, Click-to-Chat URL, handoff, confirmation, Copy/Share fallback.

## Phase 8 — Reliability
Idempotency, retry, offline queue, sync conflict handling.

## Phase 9 — QA
Unit, widget, integration, native, E2E, security, accessibility and performance.

## Phase 10 — Release
Device matrix, internal/closed testing, privacy review, subscription verification, staged release.

---

# SOURCE DOCUMENT: `docs/FILE_STRUCTURE.md`

# AI-Birthday — Target File Structure

```text
lib/
├── main.dart
├── app/
│   ├── app.dart
│   ├── router.dart
│   └── theme/
├── core/
│   ├── errors/
│   ├── database/
│   ├── security/
│   ├── logging/
│   ├── platform/
│   └── sync/
├── features/
│   ├── auth/
│   ├── onboarding/
│   ├── dashboard/
│   ├── people/
│   ├── birthdays/
│   ├── calendar/
│   ├── reminders/
│   ├── message_studio/
│   ├── ai/
│   ├── delivery/
│   ├── subscription/
│   ├── history/
│   └── settings/
└── shared/
    ├── widgets/
    ├── models/
    └── design_system/

android/app/src/main/kotlin/.../
├── ai/
│   ├── GeminiNanoBridge.kt
│   ├── GeminiNanoManager.kt
│   └── GeminiNanoErrors.kt
├── contacts/
├── notifications/
└── platform/
```

Important files:
- `ai_router.dart`: only provider-selection policy.
- `user_gemini_api_provider.dart`: user credential provider.
- `gemini_nano_provider.dart`: Flutter side of native Nano integration.
- `GeminiNanoBridge.kt`: Kotlin AICore/ML Kit implementation.
- `whatsapp_handoff_service.dart`: official link construction and handoff.
- `entitlement_repository.dart`: app subscription state.
- `message_studio_controller.dart`: draft/edit/review lifecycle.
