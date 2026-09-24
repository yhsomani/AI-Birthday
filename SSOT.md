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
