# 01 — Definitive Product Definition and Strategy

**ID:** BP-PROD-001 · **Version:** 1.0 · **Status:** Proposed approval baseline; some constraints already approved in the parent blueprint.

## 1. Product thesis

AI-Birthday is a private, local-first birthday relationship assistant. It turns user-owned birthday information and relationship context into a dependable cycle: remember the person, know when to act, prepare a suitable message, review it, hand it to a user-controlled channel, confirm the real outcome and retain useful history.

AI is an optional preparation engine. The product is the complete remembering-and-celebrating workflow, not the model, provider or a chat interface.

**Value loop:** `REMEMBER → PLAN → PREPARE → REVIEW → HAND OFF → USER CONFIRMS → LEARN/HISTORY → RETURN`

## 2. Product evidence classification

| Statement | Classification |
|---|---|
| Flutter/Dart, Kotlin, Firebase and an optional cloud direction exist | Verified repository fact at inspected baseline |
| Birthday assistant, local-first behavior and reviewed WhatsApp handoff are established project direction | Existing project decision; stakeholder reconfirmation recommended |
| Entitlement gates all AI providers, including BYOK and Nano | Approved product rule; implementation evidence is not universal runtime proof |
| New People require valid birthday; autoPrepare defaults false; per-Person Feb-29 preference; recipient timezone then device local | Approved Phase 2.3.1 product decisions |
| Strong market demand, pricing, willingness to pay, supported geography and target platform beyond Android | Unresolved assumption; requires research/approval |
| Cloud backup/backend/public Hosting is production-deployed | Not verified; current docs explicitly limit deployment claims |
| Autonomous WhatsApp, bidirectional multi-device sync and general AI/social features are intended | Not in approved core scope |

## 3. Vision, mission, objectives

**Vision:** Make celebrating important people dependable, personal and low-friction without surrendering control of their data or message.

**Mission:** Convert trusted birthday facts into timely, thoughtful, user-approved actions while preserving privacy, truthful state and recoverability.

**Objectives:**
1. Establish a reliable personal record for each important recipient.
2. Derive the right annual occurrence and next action consistently across all views.
3. Provide a useful manual path regardless of AI/provider/network state.
4. Make optional AI grounded, entitled and editable.
5. Keep delivery evidence truthful: handoff, user-confirmed send and external delivery are separate.
6. Make backup, restore, export and deletion understandable and verifiable.
7. Support the full lifecycle accessibly across supported screens and platform states.
8. Operate only capabilities with tested contracts and evidence-backed claims.

No market or numerical objective is asserted until research and baseline measurement exist.

## 4. Target users and actors

### Primary: individual birthday organizer
Needs: remember family/friends/colleagues; prepare on time; use private relationship details safely; retain final control; recover data when devices/accounts change.

### Supporting actors
- **Authenticated account owner:** uses optional subscription, backup, restore and account deletion.
- **Recipient:** contact endpoint and subject of user-managed data; has no direct app access in core scope.
- **Android/platform OS:** permissions, alarms, notifications, contacts access and app launching.
- **AI provider:** returns draft text after entitlement/capability checks; has no authority over records or sending.
- **Store authority:** verifies purchases; cannot be replaced by client state.
- **Messaging app/share target:** receives user-initiated content; app cannot infer delivery.
- **Support operator (proposed):** resolves requests through least-privilege, non-content workflows.
- **Release/operations operator (proposed):** manages environments, observability, incidents and deletion completion.
- **Autopilot operator (proposed):** restricted service role for installation leases and delivery coordination; never a source of message content or phone numbers.

## 5. Problem, value proposition and positioning

**Problem:** important birthdays are scattered across contacts/calendars/messages; generic reminders stop at awareness; thoughtful messages take effort; AI tools can invent facts or lose user control; external communication status is often misreported.

**Value proposition:** one private, reliable place to maintain the people who matter, know the exact next action, prepare a grounded greeting quickly, and retain user control over delivery. Manual handoff is always distinct from any future occurrence-scoped, explicitly approved automatic-SMS action.

**Positioning boundary:** personal birthday preparation assistant, not a CRM, bulk/campaign sender, calendar replacement, AI chat app, social network or autonomous WhatsApp platform. Automatic SMS, where permitted, is limited to a per-recipient opt-in and exact-occurrence message approval.

Competitive review is an unresolved discovery task. A team may compare personal calendars, contact reminders, notes, reminder apps and general AI writing tools, but no market-superiority or competitor claim belongs in requirements without evidence.

## 6. Product ecosystem domains

| ID | Domain | Outcome | Modules |
|---|---|---|---|
| DOM-01 | Entry and shell | Reach value safely | onboarding, sign-in, navigation, deep links, capability discovery |
| DOM-02 | Relationship memory | Trustworthy recipient context | people CRUD, validation, facts, contacts/CSV, duplicate review, export |
| DOM-03 | Birthday engine | Correct occurrence | Gregorian recurrence, leap-day, timezone, cycle identity/lifecycle |
| DOM-04 | Planning | See and prioritize | dashboard, calendar, upcoming/actions/today |
| DOM-05 | Reminders | Timely prompt | offsets, quiet hours, permissions, native schedule/reconciliation |
| DOM-06 | Message studio | Durable, useful greeting | manual edit, revisions, autosave, review, templates if approved |
| DOM-07 | AI assistance | Faster grounded drafting | entitlement, BYOK/Nano routing, operations, safety, fallback |
| DOM-08 | Delivery/history | Truthful user action | WhatsApp/SMS/copy/share, handoff, confirmation, history |
| DOM-09 | Account/subscription | Trusted optional access | identity/session, Play purchase, entitlement, restore |
| DOM-10 | Continuity/privacy | Recover/control personal data | backup/restore, export, sign-out, deletion, retention |
| DOM-11 | Automation/reliability | Safe deferred work | durable jobs, retry/cancel, occurrence-scoped auto-SMS with explicit approval; auto-prepare remains a separate decision |
| DOM-12 | Platform operations | Sustainable product | logs, monitoring, support, releases, incidents |
| DOM-13 | Public support (conditional) | Legal/help/deletion entry | hosting, privacy/terms/support/status |
| DOM-14 | Autopilot control plane | Coordinate approved Android work without personal content | installation lease, claim/fence, attempts, deletion gating; not an SMS transport |

## 7. Scope policy

### Committed core scope baseline
People; birthday occurrence; dashboard/calendar; reminders; manual Studio/drafts; entitlement-gated AI; WhatsApp/SMS composer/copy/share; truthful history; settings/accessibility; optional identity/backup/export/deletion; reliable jobs and operations.

### Approved product scope with gated activation
Android Birthday Autopilot and automatic SMS with explicit per-person opt-in and exact-message review. Keep production sending disabled until safety, policy, and device gates pass; retain manual SMS handoff in Play if policy does not permit automatic sending.

### Other conditional scope
On-device Nano; auto-preparation; cloud backup production; public legal/support/deletion site and its identity; additional import formats; localization beyond approved launch languages.

### Excluded unless separately approved
Autonomous WhatsApp; bulk/campaign messaging; social network/directory; public profiles; general AI chat; scraped messaging data; unrequested calendar/social/email integration; speculative CRM/collaboration; multi-user families/teams; analytics of private content; unsupported platform parity claims.

Every candidate capability must name the user problem, owner domain, consent/privacy impact, data owner, dependency, measurable acceptance, security/operational cost and approving authority.

## 8. Success framework

Candidate outcome measures, after privacy and event design approval:
- people with valid birthday and reviewed context;
- upcoming occurrence consistency across dashboard/calendar/reminders;
- draft completion without requiring AI;
- AI output edited/saved versus abandoned (no body tracking);
- user-confirmed action rate, separated from launch/handoff;
- restore/export/deletion success;
- reminder registration/reconciliation success by permission/capability state;
- support/incidents and accessibility task completion.

Do not set targets, conversion assumptions or monetization promises until baseline data and customer evidence exist.

## 9. Discovery backlog requiring stakeholders

- Interview target users and observe their current birthday workflow.
- Compare alternatives and unmet needs without unsupported competitor claims.
- Validate paid AI/BYOK/Nano value, subscription packaging and price sensitivity.
- Approve supported platforms, geographies, languages and distribution policy.
- Decide backup versus bidirectional sync and cloud data-retention model.
- Validate automatic preparation/SMS/control-plane product need and legal/operational owner.
- Define support, deletion and incident service expectations.
- Research accessibility and privacy expectations with representative users.

## 10. Product approval criteria

- [ ] Vision, problem and non-goals approved.
- [ ] Primary user and initial platform/geography approved.
- [ ] Conditional surfaces assigned owners or excluded.
- [ ] Commercial model research approved separately from entitlement engineering.
- [ ] Analytics/privacy policy approved before instrumentation.
- [ ] Operational support owner and supportable claims approved.
