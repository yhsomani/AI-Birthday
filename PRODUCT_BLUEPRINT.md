# RelateAI Product Blueprint

Version: 1.0.0
Date: 2026-06-26
Source documents: [PLAN.md](PLAN.md), [SSOT.md](SSOT.md)
Status: Product execution blueprint

## 1. Product Definition

RelateAI is a private relationship operating assistant for Android. It helps a user remember important people, understand relationship context, prepare personal messages, approve or automate delivery, and recover their local relationship data without relying on a custom server.

It should feel like a calm daily command center, not a novelty AI writer. The best version of the product does four things better than a calendar, contacts app, or generic AI chat:

1. It knows which relationships need attention.
2. It prepares context-aware action at the right time.
3. It gives the user clear control before anything sensitive happens.
4. It keeps private relationship data local, encrypted, and restorable.

## 2. Product Promise

For Android users who manage many personal and professional relationships, RelateAI prevents missed moments and reduces the effort of staying thoughtful. It imports contacts, discovers events, enriches each relationship with memories and history, drafts personalized messages, routes them through safe approval modes, and sends through SMS, WhatsApp, or Gmail only when the user has configured and authorized that behavior.

The product is not "AI sends messages for you." The product is "never lose a relationship moment, and never lose control."

## 3. Target Users

| Persona | Need | Product value |
| --- | --- | --- |
| Busy family connector | Tracks family birthdays, anniversaries, and regular check-ins | Reminders, warm drafts, VIP approval |
| Professional networker | Maintains clients, colleagues, mentors, alumni, vendors | Formal tone, work anniversaries, email/SMS routing |
| High-volume contact manager | Has hundreds or thousands of contacts | Sync, dedupe, event discovery, health scoring |
| Privacy-conscious user | Wants AI help without a central app server | Local-first storage, encrypted backup, explicit external integrations |
| Automation cautious user | Wants help but not accidental sends | Smart approve, VIP approve, always ask, action logs |

## 4. Product Principles

| Principle | Meaning |
| --- | --- |
| Trust before automation | No send path is allowed unless eligibility, schedule, channel, permission, and user policy are clear |
| Context creates quality | Generic contact data produces generic messages; the app should guide users to add memory, gift, style, and relationship context |
| Review is a first-class workflow | Approval, rejection, editing, regeneration, and revoke are not secondary states |
| Local-first by default | Relationship data lives on device; external APIs are explicit and explainable |
| Recoverability matters | Encrypted backup/restore is part of the core product, not an advanced setting |
| Operational clarity | AI Doctor, activity logs, and readiness states explain why something will or will not work |
| Small daily actions | The app should surface a few high-value relationship actions instead of overwhelming the user |

## 5. Product Experience Model

RelateAI has five primary surfaces:

| Surface | User question answered |
| --- | --- |
| Home | What needs my attention today? |
| Contacts | Who do I know, and what context is missing? |
| Events | What moments are coming up? |
| Messages | What drafts need review, approval, or recovery? |
| Settings / AI Doctor | Is the app ready to automate safely? |

Supporting surfaces deepen context:

- Contact Detail: relationship profile, automation preference, history, memories, gifts.
- Wish Preview: edit, choose tone, see why the draft was generated, approve or reject.
- Memory Vault: facts and moments that improve personalization.
- Gift Advisor: gift history and suggestions.
- Analytics: health, coverage, trends, export.
- Backup Restore: recoverable encrypted local data.

## 6. End-to-End User Journey

### Journey 1: First Setup

1. User opens RelateAI.
2. App explains local-first relationship assistance and approval-first automation.
3. User signs in with Google or chooses guest/local mode.
4. App asks for contacts permission only when sync is requested.
5. App imports contacts from Google and/or device.
6. App discovers birthdays, anniversaries, and work anniversaries.
7. App shows setup readiness: AI, notifications, SMS, WhatsApp, email, exact alarm, backup.
8. User selects global automation mode, defaulting to Smart Approve.
9. User completes a first encrypted backup prompt after data import.

Success state:

- Home shows upcoming events, pending setup issues, and the first recommended relationship actions.
- Nothing can auto-send until the relevant channel and approval policy are ready.

### Journey 2: Daily Relationship Command Center

1. User opens Home.
2. Home shows upcoming events, pending reviews, low-health relationships, stale backup status, and setup blockers.
3. User taps one action: review a draft, enrich a contact, sync contacts, fix setup, or create a backup.
4. Completion updates activity history and health/coverage metrics.

Success state:

- The user can understand the next best action in under 10 seconds.
- The dashboard is actionable, not only informational.

### Journey 3: Contact Enrichment

1. User opens Contacts.
2. List highlights contacts missing event dates, relationship type, preferred channel, memories, or automation settings.
3. User opens Contact Detail.
4. User adds nickname, relationship type, interests, preferred language, automation mode, send time, memory notes, and gift budget.
5. App recalculates personalization quality.

Success state:

- Contact quality visibly improves.
- Future AI drafts become more specific and explain which context was used.

### Journey 4: Event to Message

1. Event discovery finds a birthday, anniversary, work anniversary, holiday, revival, or follow-up opportunity.
2. Message generation builds a context-aware prompt from contact data, event type, style profile, memories, gifts, and prior messages.
3. AI returns variants or app uses event-aware fallback copy.
4. Quality gate checks fallback, generic text, length, personalization, route eligibility, and automation mode.
5. App creates a pending message with explicit readiness.

Success state:

- Draft is appropriate for event type and relationship.
- Automation is downgraded when quality or route readiness is weak.

### Journey 5: Review and Approval

1. User sees pending draft in Messages or notification.
2. Wish Preview shows variants, selected channel, send time, why signals, fallback state, and risk labels.
3. User edits, regenerates with feedback, rejects, approves, or schedules.
4. App recalculates readiness after every edit or regeneration.
5. Approved draft waits until scheduled time.

Success state:

- The user always knows if the message is scheduled, waiting for approval, blocked, or already sent.
- No future scheduled message sends early.

### Journey 6: Dispatch and Follow-Up

1. At scheduled time, dispatch eligibility policy evaluates status, approval mode, quiet hours, blackout dates, channel readiness, permissions, and duplicate guards.
2. Dispatcher tries eligible routes in order.
3. App records success, pending delivery, failure, fallback route, or dead letter.
4. Activity history and analytics update.
5. Follow-up or revival drafts are generated only when appropriate.

Success state:

- Delivery is reliable and auditable.
- Failures become actionable setup or retry states.

### Journey 7: Recovery and Migration

1. User exports encrypted backup after meaningful data changes or a 30-day reminder.
2. Backup includes relationship data and non-secret preferences.
3. Backup excludes tokens, passwords, API keys, and device-bound keys.
4. Restore validates manifest, previews record counts, then imports transactionally.

Success state:

- A user can recover core relationship data on a new install.
- Secrets must be re-entered, which is intentional and clearly explained.

## 7. Best Product Shape

### 7.1 Home as the Product Hub

Home should be the daily command center:

- Today and next 7 days: events and message workload.
- Needs review: drafts waiting for approval.
- Needs setup: blockers that prevent AI, sync, or delivery.
- Needs context: high-value contacts missing details.
- Needs care: low-health or stale relationships.
- Needs backup: stale or never-backed-up data.

Home should not be a generic dashboard with every metric. It should rank actions by urgency and user value.

### 7.2 Contacts as the Data Quality Engine

Contacts should not only list people. It should help the user improve the system:

- Quality chip: Ready, Needs event, Needs channel, Needs context, Needs backup-safe review.
- Quick filters: upcoming event, needs review, low health, missing channel, missing relationship, VIP, automation enabled.
- Bulk enrichment where safe: classify unknowns, set default channel, mark work contacts, create local-only contacts.

### 7.3 Events as the Moment Planner

Events should explain origin and certainty:

- Source: Google, device, manual, merged, inferred.
- Verification: verified, imported, conflict, invalid.
- Action: generate wish, set reminder, edit date, merge duplicate, dismiss this year.

### 7.4 Messages as the Control Room

Messages should be organized by state:

- Needs review.
- Scheduled.
- Blocked.
- Sent.
- Failed.
- Expired/rejected.

Each message row must show:

- Contact, event, scheduled time.
- Approval mode.
- Channel route.
- Readiness reason.
- One primary action.

### 7.5 AI Doctor as Operational Support

AI Doctor should answer:

- Can AI generate?
- Can contacts sync?
- Can notifications show?
- Can exact scheduling work?
- Can each channel send?
- Are recent failures repeating?
- Why are messages generic?
- What is the next setup fix?

It should produce one ranked fix at a time, not a long undifferentiated checklist.

## 8. Automation Policy in Product Language

| Mode | User meaning | Product behavior |
| --- | --- | --- |
| Fully Auto | Send good low-risk messages for me | Sends at scheduled time only if quality, schedule, route, and permission gates pass |
| Smart Approve | Let me review, but do not make me babysit | Shows review prompt; auto-sends at scheduled time if not rejected |
| VIP Approve | Important people require my explicit approval | Never auto-sends; expires if ignored past approval window |
| Always Ask | I want full manual control | Never auto-sends; waits for explicit approval |
| Default | Use global policy unless contact requires stricter treatment | Resolves through global mode and relationship defaults |

Non-negotiable rule:

- Authorization and schedule are separate. `APPROVED` authorizes sending; `scheduledForMs` controls when sending is allowed.

## 9. Product Data Model

| Product object | User meaning | Important fields |
| --- | --- | --- |
| Contact | A person or relationship profile | name, channels, relationship, language, style, events, memories, gifts, automation preference, health |
| Event | A relationship moment | type, date, source, verification, next occurrence, reminder |
| Draft | A prepared message | variants, selected text, event, channel, approval mode, quality, status, schedule |
| Send | A delivery attempt or result | channel, status, route, timestamp, failure reason |
| Memory | Context for better personalization | category, content, sensitivity, pinned, prompt eligibility |
| Gift | Past or planned gift context | name, cost, occasion, feedback, avoid-repeat |
| Activity | User or automation event | type, severity, status, route, references, redacted details |
| Backup | Recoverable local data snapshot | version, counts, encrypted payload, excluded secrets |

## 10. Trust and Safety Requirements

Required before broad release:

- No send before scheduled time.
- No send without route eligibility.
- No send when permission is missing.
- No send during quiet hours or blackout dates.
- No fully automatic send for fallback/generic low-quality AI output.
- No AI prompt includes user-excluded sensitive memory.
- No backup contains OAuth tokens, API keys, email passwords, or DB keys.
- No log contains raw tokens, passphrases, full phone numbers, or full message bodies.
- No deep link bypasses biometric lock.

## 11. Success Metrics

Local-only metrics can be computed on device:

| Metric | Why it matters |
| --- | --- |
| Upcoming moments covered | Measures whether events are discovered and actionable |
| Drafts generated per event | Shows automation coverage |
| Review completion rate | Shows whether approval workflow is usable |
| On-time send rate | Measures scheduling reliability |
| Blocked send reasons | Guides setup improvements |
| Personalization quality score | Predicts AI quality |
| Backup freshness | Measures recoverability |
| Relationship health distribution | Shows whether the product creates relationship action |
| Failed channel rate | Finds SMS/WhatsApp/email reliability issues |

Do not optimize for raw auto-send count. Optimize for correct, trusted, timely actions.

## 12. Release Definition

### Internal Alpha

Goal: safe local workflow on developer devices.

Must have:

- P0 dispatch and AI contract bugs fixed.
- JDK/test environment working.
- Unit tests for dispatch policy, AI fallback, classification schema, and event merge.
- Manual test with fake contacts and no live sends.

### Private Beta

Goal: real-device workflow with trusted testers.

Must have:

- Contacts permission and sync states clear.
- Backup v2 implemented.
- No-route and blocked-send states clear.
- SMS/email tests only to tester-controlled recipients.
- WhatsApp automation behind explicit setup and warning.
- Activity log shows all automation actions.

### Production Candidate

Goal: durable product quality.

Must have:

- Unit, lint, assemble, and key instrumentation tests passing.
- Backup/restore round trip validated.
- Privacy/security checklist complete.
- Release signing and pin expiry gates passing.
- Store-facing copy matches local-first, approval-first behavior.

## 13. Product Milestones

| Milestone | Outcome |
| --- | --- |
| M0: Documentation and build readiness | Product intent, tasks, and Java/Gradle validation are clear |
| M1: Automation safety | Dispatch policy is unified and no early send is possible |
| M2: AI contract correctness | AI fallback and classification schemas are reliable |
| M3: Data integrity | Events, channels, regeneration, and sync states are correct |
| M4: Recoverability | Backup v2, DB key migration, and redaction are safe |
| M5: Product UX | Home, Contacts, Events, Messages, AI Doctor express next actions clearly |
| M6: Runtime validation | Device/emulator smoke, widget/deep link, and live integration checks are complete |
| M7: Release candidate | CI, security, privacy, performance, and documentation gates pass |

## 14. What Not to Build Yet

Avoid these until core reliability is proven:

- A custom backend.
- Social features or shared relationship graphs.
- Cloud backup.
- Bulk auto-send campaigns.
- Complex CRM replacement features.
- AI that invents personal facts not in local context.
- Monetization flows that distract from trust, safety, and reliability.

## 15. North-Star Product Statement

RelateAI becomes the best product when it is the trusted daily layer between contacts, calendar-like moments, personal memory, AI writing, and controlled delivery. It should make thoughtful communication easier while making risky automation harder to do accidentally.

