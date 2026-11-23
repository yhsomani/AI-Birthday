# 02 — Product scope and domain map

## 1. Product decomposition

`AI-Birthday` is decomposed into domains by ownership of user outcomes and data. This is a product taxonomy, not a statement that each domain must become a separate deployable service.

| Domain ID | Domain | User/business outcome | Core modules |
|---|---|---|---|
| DOM-01 | Onboarding and navigation | Understand value and reach the next useful action | First-run, shell, routing, deep links |
| DOM-02 | People and relationships | Maintain accurate recipient context | Person records, validation, import, export, duplicate review |
| DOM-03 | Birthday lifecycle | Resolve recurring occurrences correctly | Date rules, cycle generation, rollover, status |
| DOM-04 | Calendar and planning | See birthdays by date and month | Month grid, day detail, navigation |
| DOM-05 | Dashboard and action center | Identify who needs attention now | Today, upcoming, action-needed, quick actions |
| DOM-06 | Reminders and notifications | Notify at configured times without surprising the user | Schedule rules, quiet hours, permissions, native delivery |
| DOM-07 | Message preparation | Create and maintain a suitable greeting | Manual editor, drafts, autosave, templates if approved |
| DOM-08 | AI assistance | Generate or transform grounded message drafts | Entitlement gate, provider routing, prompt construction, rewrite, translation |
| DOM-09 | Delivery and confirmation | Hand off message to a channel and record truthful outcome | WhatsApp, SMS composer, personal SMS flavor, copy/share, confirmation |
| DOM-10 | History and lifecycle review | Understand prior preparation and confirmed actions | Draft history, handoff events, confirmed status |
| DOM-11 | Identity, subscription and entitlements | Secure account features and verify paid AI access | Google sign-in, session, Play purchase, server verification |
| DOM-12 | Data continuity | Back up and restore data with integrity | Local database, opt-in cloud backup, manifests, conflict handling |
| DOM-13 | Durable jobs and automation | Execute deferred work reliably | Job queue, retry policy, scheduled preparation |
| DOM-14 | Preferences and design system | Configure behavior and consistent experience | Settings, accessibility, theme tokens, notification and AI settings |
| DOM-15 | Help, privacy and account lifecycle | Get help and exercise data rights | Support, legal disclosures, export, deletion |
| DOM-16 | Operational control plane (separate) | Coordinate a separately governed sender workflow | Installation lease, claim/arm, retries, deletion fences |
| DOM-17 | Public web surface (supporting) | Provide legal, support and deletion entry points | Home, privacy, terms, support, deletion |

## 2. Capability tree

```text
AI-Birthday
├── Experience
│   ├── Onboarding
│   ├── Navigation and deep links
│   ├── Dashboard / action center
│   └── Calendar
├── Relationship memory
│   ├── People
│   ├── Relationship context
│   ├── Contact import / CSV import
│   ├── Validation / duplicate review
│   └── Export
├── Birthday lifecycle
│   ├── Recurrence and next occurrence
│   ├── Leap-day policy
│   ├── Timezone policy
│   ├── Lifecycle status
│   └── Reminder planning
├── Preparation
│   ├── Manual message editor
│   ├── Draft persistence and history
│   ├── AI entitlement
│   ├── Gemini API BYOK
│   ├── Gemini Nano / AICore
│   ├── Grounded personalization
│   └── Rewrite / translate / variations
├── User-controlled action
│   ├── WhatsApp handoff
│   ├── SMS composer handoff
│   ├── Personal-flavor SMS (conditional)
│   ├── Copy / share
│   └── Explicit confirmation
├── Account and continuity
│   ├── Identity and session
│   ├── Subscription and entitlement
│   ├── Local database
│   ├── Optional cloud backup / restore
│   └── Data export / deletion
├── Reliability and operations
│   ├── Durable job queue
│   ├── Notifications and native scheduling
│   ├── Logging / redaction
│   ├── Monitoring / release evidence
│   └── Support
└── Separate control-plane product surface
    ├── Sender installation / lease
    ├── Occurrence and destination claims
    ├── Attempt authorization / retry fences
    └── Account deletion / repair operations
```

## 3. Feature registry mapping

The repository's 2026-10-14 knowledge registry contains 50 nodes: 49 marked `implemented`, one marked `partially-implemented`. Those labels mean source was located in that historical source-review snapshot; they are not verified live behavior. The registry must be refreshed against the 2026-11-20 main commit.

| Existing registry ID | Domain | Blueprint disposition |
|---|---|---|
| APP, ONB, ONB-FLOW, ONB-GATE, NAV | DOM-01 | Retain; validate onboarding comprehension and route edge cases |
| DASH | DOM-05 | Retain; define action prioritization and empty states |
| PEOPLE, PEOPLE-CRUD, PEOPLE-VALIDATE, PEOPLE-IMPORT, PEOPLE-CSV, PEOPLE-DEVICE, PEOPLE-EXPORT | DOM-02 | Retain; deep-review data loss, permissions, duplicates and import preview |
| BIRTH, BIRTH-CALC, BIRTH-CYCLE | DOM-03 | Retain; define date/timezone/leap-day rules centrally |
| CAL | DOM-04 | Retain; validate month/day behavior and accessibility |
| REM, REM-RULES, REM-PREFS, REM-NATIVE | DOM-06 | Retain; verify OS scheduling, permissions, reboot and quiet-hours semantics |
| STUDIO, STUDIO-DRAFT, STUDIO-AI, STUDIO-EDIT | DOM-07/08 | Retain; clarify autosave, job lifecycle, revision and stale result behavior |
| AI, AI-BYOK, AI-NANO, AI-PROMPT | DOM-08 | Retain; enforce subscription gate before provider selection |
| SEND, SEND-WA, SEND-SMS, SEND-SHARE, SEND-TRUTH | DOM-09 | Retain; distinguish channel and evidence-based states |
| HISTORY | DOM-10 | Retain; history must read canonical lifecycle events |
| AUTH, BILL | DOM-11 | Retain; independently verify purchase and session flows |
| CLOUD, CLOUD-UP, CLOUD-DOWN | DOM-12 | Retain; verify atomicity, conflict, deletion and privacy |
| JOBS | DOM-13 | Retain; define idempotency, lease, retry and cancellation |
| SETTINGS, THEME | DOM-14 | Retain; remove duplicate/contradictory configuration surfaces |
| AUTO-PREP | DOM-13/07 | Partially implemented in historical registry; product consent and execution behavior unresolved |
| WEB, WEB-DELETE | DOM-15/17 | Conditional release surface; external release gates unresolved |
| CTRL, CTRL-CLAIM, CTRL-DELETE, CTRL-PURCHASE | DOM-16 | Keep separate from core mobile workflow; product/deployment approval required |

## 4. Cross-domain contracts

- People owns recipient identity and user-supplied relationship context.
- Birthday lifecycle owns occurrence identity, recurrence and status transitions.
- Reminder planning consumes occurrences and user preferences; it does not own birthday dates.
- Message Studio owns editable draft intent and draft revision.
- AI provider routing consumes a draft request but does not own subscription state or recipient records.
- Delivery owns handoff attempts and confirmation events; it cannot claim external delivery unless the channel provides authoritative evidence.
- History is a read model over durable draft and delivery events, not a second independent source of truth.
- Subscription owns entitlement; provider availability is a separate capability check.
- Cloud backup transports only explicitly approved data and must preserve ownership, deletion and version semantics.
- Durable jobs execute work but do not bypass user consent, entitlement or cancellation.
- Control-plane claims/leases do not imply that mobile user messaging is automated.

## 5. Proposed future capability policy

Any new capability must satisfy all of:
1. A named user problem and measurable outcome.
2. A clear owning domain and canonical data owner.
3. A user journey and explicit permission/consent model.
4. A security/privacy review.
5. A dependency and operational-cost assessment.
6. Acceptance criteria and test mapping.
7. Stakeholder approval before entering committed scope.

No social features, contact enrichment, email/calendar integrations, campaign messaging or AI chat are included by default.

## 6. Open domain questions

- Are Android and iOS both target products, or is Android the only supported platform for the next release?
- Is Birthday Autopilot intended to be a separately sold product, an internal service, or a future optional capability?
- Is automatic SMS a supported product tier or a personal sideload-only experiment?
- Which languages and locale/date formats are required for launch?
- Which data categories are included in cloud backup and how are deletes propagated?
