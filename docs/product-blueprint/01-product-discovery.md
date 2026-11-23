# 01 — Product discovery and strategy

## 1. Product definition

**Working definition:** AI-Birthday is a privacy-conscious personal birthday assistant that helps a person remember important relationships, prepare timely and relationship-aware birthday messages, review them, hand them to a communication app, confirm the outcome and build a reliable history for future celebrations.

The product is not an AI chatbot, a bulk messaging platform, a contact CRM, or an autonomous WhatsApp sender. AI is an optional preparation capability within a human-controlled birthday workflow.

**Core value loop:**

`REMEMBER → PREPARE → PERSONALIZE → REVIEW → SEND → CONFIRM → COMPLETE → RETURN`

The loop is the organizing principle for product scope, navigation, lifecycle state, data and measurement.

## 2. Evidence and confidence

| Statement | Classification | Evidence / limitation |
|---|---|---|
| The current application is Flutter/Dart with native Android Kotlin integrations | FACT — repository | Root project files and Android source tree |
| The product currently describes itself as local-first | FACT — repository | README, SSOT, architecture and data-lineage documents |
| User-reviewed messaging is a core product boundary | FACT — repository + prior decision | README, SSOT and delivery audit |
| AI access is subscription-gated for all providers | FACT — prior decision | Explicit project decision retained from earlier work; current repository docs conflict and need correction |
| Cloud backup is optional | FACT — repository direction | README and architecture |
| Birthday Autopilot is a separate server-side coordination surface | FACT — repository | Backend README and knowledge portal |
| There is a validated market need, market size or willingness to pay | OPEN | No customer research or market evidence reviewed in this baseline |
| Subscription is the final commercial model and pricing is viable | OPEN | Code contains purchase/entitlement mechanisms; commercial validation and pricing approval are absent |

## 3. Vision, mission and outcomes

**Vision (proposed):** Make remembering and celebrating important people dependable, personal and low-friction.

**Mission (proposed):** Turn birthday information into timely, thoughtful, user-approved actions while keeping personal data and communication under the user's control.

**Product outcomes:**
1. A user can reliably capture and maintain birthday relationships.
2. The user can understand who needs attention and when.
3. The user can prepare a suitable message with or without AI.
4. AI-generated content is grounded only in user-supplied or explicitly permitted facts.
5. The user reviews and personally initiates every external message.
6. The app records only what it can truthfully know.
7. Local core workflows remain useful without sign-in or cloud connectivity.
8. Users can understand, export, back up and delete their data.

These are proposed outcome statements; quantitative targets require baseline measurement and stakeholder approval.

## 4. Primary user and jobs-to-be-done

### Primary user: birthday organizer

A person who wants to remember birthdays across family, friends, colleagues and other relationships, and wants help preparing an appropriate greeting without losing control of the message.

**Jobs:**
- When I add someone important, I want to record their birthday and enough context to recognize the relationship later.
- When a birthday approaches, I want a timely reminder with a clear next action.
- When I need to write a greeting, I want to start from a useful draft rather than a blank page.
- When I use AI, I want it to use only facts I supplied and let me edit every word.
- When I choose a delivery channel, I want the app to be honest about whether it opened another app or whether I confirmed sending.
- When I change devices or lose access, I want understandable backup and recovery choices.

### Secondary actors

| Actor | Role in the ecosystem | Status |
|---|---|---|
| App user | Owns contacts, preferences, drafts and delivery confirmations | Core |
| Android OS | Mediates alarms, notifications, contacts permission, SMS and intents | Platform actor |
| AI provider | Generates or transforms draft text after entitlement and availability checks | External dependency |
| Google identity / Firebase | Authenticates optional account features | External dependency |
| Google Play | Purchase and subscription source of truth | External dependency |
| WhatsApp / SMS app / Android share sheet | Receives a user-initiated handoff | External dependency |
| Support operator | Handles support requests under a defined support policy | Proposed; operational model open |
| Autopilot service operator | Operates a separate coordination control plane | Separate system; deployment and product ownership open |

No demographic persona, income profile, market segment size or behavioral statistic is asserted without research.

## 5. Product principles

1. **User control:** no silent external messaging; every message is user-reviewed.
2. **Truthful state:** opened, copied, handed off, user-confirmed and delivered are distinct states.
3. **Privacy by default:** keep personal data local unless the user explicitly enables a cloud or external operation.
4. **AI is optional:** manual message composition remains available when AI is locked, unavailable or offline.
5. **Grounded personalization:** never fabricate personal facts, memories or relationship details.
6. **Recoverability:** destructive operations, sync and restore must be explicit, observable and recoverable where possible.
7. **Least privilege:** ask for device and account permissions only at the point of need.
8. **Accessible utility:** reliable at large text sizes, narrow screens, assistive technology and reduced motion.
9. **Small coherent architecture:** add services only to solve demonstrated reliability, security or product problems.
10. **Evidence-driven claims:** source presence is not runtime proof; CI is not deployment proof.

## 6. Product boundaries

### In scope for the core product
- People and birthday records.
- Recurring birthday occurrence lifecycle.
- Calendar, dashboard and history.
- Reminders and notification preferences.
- Manual message composition and draft management.
- Subscription-gated AI generation and editing.
- User-controlled WhatsApp, SMS-composer, copy and share handoffs.
- Optional identity, cloud backup and restore.
- Settings, privacy, account and data lifecycle.
- Diagnostics and support pathways.

### Separate or conditional surfaces
- **Birthday Autopilot control plane:** distinct backend capability with sender leases, claims, arm/retry fences and deletion operations. It is not equivalent to the core mobile WhatsApp workflow. Productization, supported channels, legal basis, deployment and mobile integration require separate approval.
- **Public legal/help/deletion site:** supporting web surface; release is blocked until external identity, legal, privacy, support, reCAPTCHA and deployment configuration gates are met.
- **Automatic SMS in personal flavor:** platform-specific, permission-sensitive capability. It must be separately consented, reviewed, rate-limited and distribution-policy validated. It is not a default cross-platform capability.

### Explicit non-goals unless approved
- Autonomous WhatsApp sending or WhatsApp account automation.
- Bulk marketing or campaign messaging.
- Social network, public birthday directory or public profile discovery.
- General-purpose AI chat.
- Scraping private contact or messaging data.
- Unrequested calendar/email/social integrations.
- iOS parity claims without an approved iOS scope and verified implementation.
- Monetization, pricing or paid tier promises beyond verified approved decisions.

## 7. Product success measurement

Metrics below are **proposed**, not current measured values. Define event semantics and privacy review before instrumentation.

| Outcome | Candidate metric | Guardrail |
|---|---|---|
| Reliable remembering | Percentage of saved people with valid birthday records | Do not infer or enrich missing birth dates |
| Timely preparation | Reminder-to-studio open rate | No invasive tracking; opt-in analytics decision required |
| Useful message preparation | Draft completion / edit-save rate | Never measure message-body content |
| Successful user action | User-confirmed completion rate | Do not equate handoff with send |
| Retention of useful records | Returning users with upcoming birthdays maintained | No dark patterns or unnecessary notifications |
| Reliability | Reminder scheduling and persistence success in tested environments | Report permission-denied cases separately |
| Trust | Backup restore success, deletion completion and support resolution | No claim of zero-knowledge encryption without proof |

No target percentages are set until a baseline, analytics policy and stakeholder-approved objective exist.

## 8. Discovery work still required

- Customer interviews and usability observation.
- Problem validation against non-app alternatives and competing birthday/reminder products.
- Willingness-to-pay and subscription value validation.
- Privacy expectations for contacts, birthday dates, AI prompts and backups.
- Supported geography, languages and distribution channels.
- Product ownership and release responsibility for Birthday Autopilot.
- Operational support, incident response and deletion SLA.
- Accessibility research with assistive-technology users.

## 9. Strategic approval checklist

- [ ] Approve product vision and primary job.
- [ ] Approve target user and supported platforms.
- [ ] Approve explicit product boundaries and separate backend surfaces.
- [ ] Approve AI entitlement and provider policy.
- [ ] Approve commercial model and pricing research.
- [ ] Approve data/privacy model and analytics policy.
- [ ] Approve support and operational ownership.
