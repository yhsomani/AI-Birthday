# RelateAI Product Single Source of Truth

Status: **normative product reset baseline; product hypotheses require validation**

Last reviewed: 2026-07-11

Decision owner: RelateAI product owner

Audience: product, business, design, engineering, QA, support, and release owners

This document defines the product RelateAI should become if it is built today. It is intentionally business-first and end-user-first. It does not preserve a feature merely because code, tests, or earlier documentation already exist.

The current implementation is evidence about feasibility, not authority for product scope. `docs/product-reset/README.md` maps the complete audit and all requested deliverables. `docs/react-native-migration-status.md` describes implementation status only. Older feature and roadmap documents are non-normative historical inputs.

## 1. Executive Product Decision

RelateAI should **not** continue as a broad personal CRM, diagnostics suite, analytics product, or unattended messaging system. That space is already served by products such as [Dex](https://getdex.com/docs/dex-core) and [Monica](https://www.monicahq.com/features), while Apple and Google already surface contact birthdays and calendar reminders ([Apple](https://support.apple.com/en-lamr/guide/iphone/iph3d1110d4/ios), [Google](https://support.google.com/contacts/answer/12732221?hl=en)). A generic reminder or AI-writing feature is not sufficient differentiation.

The product hypothesis worth testing is narrower:

> RelateAI helps busy, privacy-conscious adults remember important relationship moments and prepare an authentic message in under a minute, without surrendering control of their contacts or sending.

The first market hypothesis is urban Indian professionals and globally distributed Indian families who communicate across English, Hindi, and Hinglish and primarily use manual messaging handoff. This segment is a hypothesis, not a proven fact. No scale build or monetization work is justified until customer discovery validates the problem, segment, and willingness to pay.

The current codebase contains useful safety and domain work, but the end-user product should be rebuilt around a much smaller task model. Reuse is selective; sunk cost is not a reason to retain scope.

## 2. What RelateAI Is—and Is Not

RelateAI is:

- a private relationship-moment assistant;
- a reminder-to-thoughtful-action workflow;
- a review-first writing aid with strong non-AI fallbacks;
- a lightweight place for user-selected context that improves future actions;
- a user-controlled bridge to the messaging app they already use.

RelateAI is not:

- a sales CRM, networking database, team workspace, or social network;
- an autonomous agent that chooses recipients or sends messages unattended;
- a replacement for Contacts, Calendar, WhatsApp, SMS, or email;
- a relationship scoring or gamification system;
- a dashboard/reporting product;
- a surveillance tool that reads private conversations or scrapes social data;
- a reason to request broad permissions before the user sees value.

## 3. Evidence and Decision Rules

Three truth levels apply:

1. **Validated** — supported by observed customer evidence or production outcomes.
2. **Tested** — verified technically or through usability testing, but not proven as a business outcome.
3. **Hypothesis** — plausible but unvalidated; must not be presented as customer truth.

As of this review, almost all product-value claims are hypotheses. The repository proves substantial technical behavior, but it contains no customer interviews, cohort retention, funnel analytics, willingness-to-pay study, production usage, support data, or signed-store evidence.

A feature enters the committed roadmap only when it:

- advances the core job to be done;
- has a named target user and observable pain;
- has a measurable outcome and owner;
- is simpler than the workaround it replaces;
- fits the trust and privacy model;
- has evidence proportional to its cost and risk.

If those conditions are not met, the default decision is **do not build**.

## 4. Target Users and Jobs

### Primary persona: the thoughtful but time-poor connector

- Maintains roughly 20–100 personally important relationships.
- Misses dates or delays messages because attention is fragmented.
- Wants messages to sound personal, not generated or generic.
- Uses a familiar messaging app and wants final control over sending.
- Will add a small amount of context if value appears immediately.
- Is uneasy about uploading an entire address book or private notes.

Primary job:

> When an important moment approaches, help me remember it, recall enough context, and prepare something genuine quickly so I can act while remaining in control.

### Secondary persona: the family relationship organizer

- Tracks birthdays, anniversaries, and family milestones across generations or locations.
- Needs simple shared-memory support but not workplace CRM features.
- Values local languages, recurring dates, and reliable reminders.

This persona is not a collaboration requirement for version one. Shared household data remains a discovery topic because it changes identity, consent, sync, and privacy substantially.

### Explicitly excluded personas

- salespeople managing pipelines;
- recruiters or professional network power users;
- marketing teams sending campaigns;
- users seeking unattended bulk messaging;
- users seeking to monitor another person's communications.

## 5. User Outcomes

The product succeeds only if users can:

1. Capture a person and important moment with minimal effort.
2. Trust that they will be reminded at the useful time.
3. Understand the next action immediately.
4. Prepare a relevant message with transparent context.
5. Edit or reject every suggestion.
6. Open their chosen messaging app and send manually.
7. Record enough optional context to make the next interaction easier.
8. export or delete their data without negotiation.

## 6. Business Goals and Business Model Hypothesis

### Business goals

1. Prove a repeatable reminder-to-action habit before expanding scope.
2. Earn trust sufficient for users to retain private relationship context.
3. Demonstrate that optional assisted writing saves meaningful time without reducing authenticity.
4. Establish a sustainable subscription proposition without ads or sale of personal data.
5. Keep support, policy, inference, and infrastructure costs below the value of the narrow workflow.

### Business model hypothesis

- The free experience should include manual people, moments, reminders, templates, export, and deletion. Core privacy and accessibility are never paywalled.
- A paid plan may offer privacy-preserving AI assistance and encrypted multi-device continuity if users demonstrate willingness to pay. Core backup, recovery, export, deletion, privacy, and accessibility remain free.
- Exact pricing is a discovery output, not a specification. Test pricing concepts before building billing.
- Advertising, contact-data monetization, affiliate gift commerce, and sale of behavioral data are prohibited because they conflict with the trust proposition.

### North-star metric

**Monthly meaningful actions completed**: unique user-confirmed actions that began from an active moment or keep-in-touch reminder and ended in a reviewed handoff, a completed check-in, or an intentionally dismissed reminder.

This metric must not reward message volume. A single thoughtful action is more valuable than bulk activity.

### Initial validation metrics

These define the initial beta measurements. Any numerical threshold below is a provisional strawman for research planning, not an approved release or investment gate:

| Outcome          | Initial measurement                                                                                                                 |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Activation       | User creates/imports at least one relevant person, saves one moment, and reaches a useful preview or reminder in the first session. |
| Time to value    | Median time from install to first useful reminder or reviewed message is under five minutes.                                        |
| Core efficiency  | Median time from reminder open to messaging-app handoff is under one minute.                                                        |
| Habit            | Qualified users return and complete at least one meaningful action in the following month.                                          |
| Draft usefulness | Users keep or lightly edit a majority of assisted drafts; heavy rewrites and repeated regeneration are failure signals.             |
| Trust            | Zero unattended sends; duplicate-send escapes and private-context leaks are release-blocking incidents.                             |
| Reliability      | Reminder delivery, data recovery, and handoff completion are measured separately; no aggregate vanity score hides failure.          |
| Monetization     | Willingness to pay is tested through interviews and a reversible offer before billing implementation.                               |

After baseline research, the product owner must accept, revise, or remove each numerical threshold. A metric without an event definition, denominator, segment, and owner is not accepted.

## 7. Experience and Information Architecture

### Primary navigation

1. **Today** — upcoming moments and the next useful actions.
2. **Moments** — important dates and recurring reminders.
3. **People** — the small set of relationships the user chose to manage.

Compose and review are contextual actions, not permanent navigation destinations. Settings, privacy, backup, help, and diagnostics live behind the account/settings entry. A review queue may become a primary destination only if beta behavior proves sustained demand.

### Today

Today is an action list, not a dashboard. It shows:

- due and upcoming moments;
- drafts that need review;
- reminders that need a decision;
- one clear primary action per item;
- a small quick-add action.

It must not show relationship health scores, charts, setup percentages, report exports, or generic engagement metrics.

### Progressive disclosure

- Manual entry works before any permission request.
- Contact selection is requested only when the user chooses import.
- Notification permission is requested only after a reminder has been created and its benefit explained.
- Optional AI is introduced only from the compose workflow.
- Biometrics and backup are offered after private context exists.
- Operational detail appears only when an action fails or the user opens Help.

Google Play has announced a minimum-scope Contacts policy effective 2026-10-28 and recommends the Contact Picker where broad access is unnecessary ([policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)). The product therefore defaults to selective import; broad address-book access requires separate proof that it is essential.

## 8. Canonical Business Workflows

### 8.1 Acquire and activate

1. Explain one outcome: remember a moment and prepare a thoughtful action.
2. Let the user try with manual data or a single selected contact.
3. Produce first value before account creation, AI setup, notification permission, or payment.
4. Ask for the next permission only in context.
5. After explicit analytics opt-in, measure activation without recording names, message content, dates, or contact routes.

### 8.2 Reminder to meaningful action

1. A verified moment produces a reminder at the user's chosen lead time.
2. The reminder opens the exact moment, not a generic dashboard.
3. The user writes manually, chooses a template, or requests optional assistance.
4. The user reviews and edits the final text.
5. RelateAI opens the chosen external messaging app or copies the text.
6. Only the user can confirm completion.
7. The product may offer one optional follow-up; it never invents one silently.

### 8.3 Assistance and provider operations

1. Show which context categories will be used before an external request.
2. Exclude private notes by default.
3. Minimize and bound the payload.
4. Use a short-lived authenticated backend session; never put provider secrets in the client.
5. Return multiple editable suggestions or a clear local fallback.
6. Record only content-free reliability and cost events.
7. Never claim AI is ready when the provider path is unavailable.

### 8.4 Trust, support, and deletion

1. The user can inspect data categories, permissions, and external processing.
2. Export produces a portable user-owned file.
3. Delete explains local, backup, and server effects before confirmation.
4. Recovery failures stop mutation and offer a safe path.
5. Support diagnostics use correlation metadata, never relationship content.

### 8.5 Monetization and entitlement

1. Test willingness to pay with interviews and a reversible offer before implementing billing.
2. Keep people, moments, reminders, local templates, manual handoff, privacy, accessibility, export, and deletion in the free core.
3. Offer a paid assisted-writing or encrypted-continuity benefit only after its value and operating cost are proven.
4. Entitlement failure falls back to the complete free workflow and never strands user data or an in-progress draft.
5. Cancellation explains the effective date, preserves export/deletion, and never uses relationship data for retention pressure.

### 8.6 Release, incident, and support operations

1. A release candidate is bound to source, signed artifacts, device evidence, policy declarations, and rollback ownership.
2. Privacy, wrong-recipient, duplicate-action, reminder, restore, or provider incidents enter a content-free severity taxonomy.
3. The owner can disable assisted composition or another failing optional capability without disabling the local core.
4. Affected users receive accurate impact and recovery guidance without exposing another person's data.
5. A post-incident review updates the relevant SSOT rule, ADR, test, runbook, or release gate; it never waives evidence through prose.

## 9. Feature Contracts

### 9.1 First Value and Contextual Onboarding

**Why it exists:** New users need to understand and experience value before setup burden.

**Who uses it:** Every new or returning user whose setup is incomplete.

**Problem:** A multi-step configuration tour creates abandonment before trust is earned.

**Expected behavior:** A three-screen maximum introduction leads to manual entry or selective contact import, then one moment and one useful outcome. Permission, AI, backup, and biometric prompts occur later in context.

**Business value:** Improves activation and makes acquisition spend measurable.

**User value:** Delivers a useful result without surrendering an address book or understanding system internals.

**Success criteria:** Users can reach first value without an account or optional permission; each deferred setup step has a clear later entry point.

**Acceptance criteria:**

- Skip is always available for optional setup.
- Unavailable capabilities are never offered as selectable goals.
- The product never initializes with sample relationship data.
- Abandoning onboarding preserves valid progress and resumes at a useful point.
- Permission denial leaves a complete manual alternative.

### 9.2 People

**Why it exists:** Moments and messages need a minimal person identity and route.

**Who uses it:** Users tracking a deliberately chosen relationship.

**Problem:** Native contacts contain too much data, while a full CRM requires too much maintenance.

**Expected behavior:** Create manually or select specific contacts; store name, preferred route, language, relationship label, and optional context. Merge only on reviewed exact evidence.

**Business value:** Creates the durable context that increases repeat value and switching cost without becoming a CRM.

**User value:** Avoids re-entering who a person is and how to contact them.

**Success criteria:** Most active users maintain a small, useful set rather than importing an unused address book.

**Acceptance criteria:**

- Manual creation is first-class.
- Selective import previews fields before saving.
- Name alone never causes an automatic merge.
- Edit, archive, merge, export, and delete effects are explained.
- No hidden relationship score is stored or shown.

### 9.3 Moments

**Why it exists:** Remembering important dates is the entry job.

**Who uses it:** Users who do not want to miss birthdays, anniversaries, or a custom recurring moment.

**Problem:** Native calendars can show dates, but they do not connect the date to context-aware preparation and review.

**Expected behavior:** Add birthday, anniversary, or custom moment; choose recurrence and reminder lead time; resolve duplicates explicitly. Advanced event taxonomies remain out of the primary flow.

**Business value:** Supplies the recurring trigger for retention.

**User value:** Replaces mental tracking and turns a date into an actionable preparation window.

**Success criteria:** Saved moments reliably reappear and lead to action rather than notification dismissal.

**Acceptance criteria:**

- Date-only values stay date-only across time zones.
- Year-unknown birthdays and annual recurrence are supported.
- Conflicts are previewed; no source silently overwrites another.
- Reminder lead time and local time are visible and editable.
- Import is optional and reversible.

### 9.4 Today and Reminders

**Why it exists:** Users need one place to know what deserves attention now.

**Who uses it:** Returning users and users opening a notification.

**Problem:** A dashboard full of metrics does not answer “what should I do next?”

**Expected behavior:** Rank due moments and review work by urgency, show one next action, and allow snooze, dismiss, or complete.

**Business value:** Drives the repeat habit and exposes reminder reliability.

**User value:** Reduces decision effort and avoids searching across features.

**Success criteria:** A user can understand and act on the first item without opening another overview screen.

**Acceptance criteria:**

- Counts match actionable records.
- Notifications deep-link to the exact current item.
- Stale or deleted targets recover to a useful list with an explanation.
- Denied notification permission never blocks in-app reminders.
- Snooze and dismiss are explicit and reversible where practical.

### 9.5 Compose Assistance

**Why it exists:** The hard part is often knowing what to say, not remembering the date.

**Who uses it:** Users preparing a message for a selected person and moment or a manual check-in.

**Problem:** Generic generated text feels inauthentic; blank-page writing takes time.

**Expected behavior:** Start from manual text, a local template, or optional AI; show the context categories and tone; produce editable suggestions; work offline with templates.

**Business value:** This is the likely paid-value differentiator, subject to willingness-to-pay validation.

**User value:** Saves time while preserving authorship and recipient appropriateness.

**Success criteria:** Users accept or lightly edit suggestions and do not repeatedly regenerate to escape poor context.

**Acceptance criteria:**

- AI unavailability never blocks manual or template composition.
- Private notes are excluded unless the user explicitly selects a safe excerpt for one request.
- Language and tone are chosen per recipient, not hidden in a global score.
- Suggestions never fabricate facts or imply a relationship detail absent from selected context.
- Too-short or channel-invalid text receives actionable feedback.

### 9.6 Review and Manual Handoff

**Why it exists:** Sending the wrong personal message is the highest-trust failure.

**Who uses it:** Every user who finishes a draft.

**Problem:** Automatic sending removes context and control; copying manually adds friction.

**Expected behavior:** Present final recipient, route, text, timing, and duplicate warning; then open the destination app or copy/share. The destination app performs the send.

**Business value:** Preserves trust and avoids policy-heavy direct delivery infrastructure.

**User value:** Saves steps without creating accidental or hidden sends.

**Success criteria:** Zero unattended sends and near-zero duplicate or wrong-recipient escapes.

**Acceptance criteria:**

- Opening another app never marks the message sent.
- Completion requires explicit confirmation.
- Recipient, approval freshness, route, duplicate risk, and body limits are rechecked immediately before handoff.
- A copy/share fallback is always available.
- Bulk approve, bulk send, and global auto-send are absent from the initial product.

### 9.7 Context and Relationship Timeline

**Why it exists:** Small user-chosen details make future actions more relevant.

**Who uses it:** Users who want continuity across moments and interactions.

**Problem:** Separate Memory Vault, Gift Advisor, Chat History, and Activity History destinations create maintenance and navigation overhead.

**Expected behavior:** One chronological person timeline holds brief notes, past completed actions, important moments, and optional gift ideas.

**Business value:** Improves retention and assisted-writing quality without broadening into a CRM.

**User value:** Provides “where did we leave off?” context in one place.

**Success criteria:** Context is added because it helps a near-term action, not because the app demands profile completion.

**Acceptance criteria:**

- Notes are optional, editable, exportable, and deletable.
- Private notes are visually distinct and excluded from assistance by default.
- The timeline contains user-meaningful events, not infrastructure logs.
- Gift ideas are simple notes; budgets, recommendation engines, and commerce are out of scope.
- Numeric relationship health and guilt-inducing streaks are prohibited.

### 9.8 Keep-in-Touch Follow-ups

**Why it exists:** Relationship maintenance extends beyond annual dates.

**Who uses it:** Users who explicitly choose a cadence for a person.

**Problem:** Good intentions to reconnect are easy to postpone.

**Expected behavior:** Set an optional cadence, receive a gentle prompt, then compose, snooze, dismiss, or mark contacted elsewhere.

**Business value:** Could create a more frequent habit than birthdays, but must be validated before becoming core.

**User value:** Helps maintain chosen relationships without judgment.

**Success criteria:** Users voluntarily retain cadences and act without high mute/dismiss rates.

**Acceptance criteria:**

- Cadence is opt-in per person.
- No inferred neglect score or public ranking exists.
- Marking contacted never requires message-content access.
- Repeated dismissal leads to a suggestion to reduce or stop reminders.

### 9.9 Data, Privacy, Backup, and Recovery

**Why it exists:** The product handles unusually sensitive third-party and relationship data.

**Who uses it:** Every user; advanced controls appear when relevant.

**Problem:** Trust collapses if data use, loss, export, or deletion is unclear.

**Expected behavior:** Local-first storage, transparent external processing, user-owned encrypted export, verified restore, delete-all, and safe recovery.

**Business value:** Trust and policy compliance are prerequisites for retention and distribution.

**User value:** Keeps the user in control and prevents lock-in or silent loss.

**Success criteria:** Users can accurately explain where data lives and complete export/delete without support.

**Acceptance criteria:**

- No relationship data is sold or used for advertising.
- All optional external processing is disclosed at the moment of use.
- Export and restore verify integrity before claiming success.
- Failed clear or restore is fail-closed and recoverable.
- App lock is optional and introduced after private data exists.
- A public privacy policy, retention policy, support contact, and store disclosures exist before beta distribution.

### 9.10 Settings, Help, and Platform Quality

**Why it exists:** Users need understandable control and recovery, not a control panel for implementation internals.

**Who uses it:** Users changing language, notification behavior, data controls, or troubleshooting.

**Problem:** Advanced settings and diagnostics expose system complexity and create inconsistent states.

**Expected behavior:** Keep only language, notification timing, assistance consent, app lock, data controls, and help. Contextual recovery links return to the failed task.

**Business value:** Reduces support burden and protects ratings.

**User value:** Makes choices understandable without technical vocabulary.

**Success criteria:** Common failures are recoverable without a generic diagnostic dashboard.

**Acceptance criteria:**

- English, Hindi, and Hinglish behavior is verified by native speakers before release.
- Large text, screen readers, reduced motion, contrast, and switch access are release requirements.
- Errors state what happened, what was not changed, and the next safe action.
- Detailed diagnostics are content-free and hidden behind Help.
- Settings never expose unavailable providers or automation modes.

## 10. Cross-Feature Business Rules

1. A person, route, moment, draft, or context record belongs to the user and is never inferred as consent from the other person.
2. No external entry point may approve, send, import, export, merge, or delete without an in-app confirmation.
3. No message is sent by RelateAI in the initial product.
4. All suggestions are drafts; the user remains the author and decision-maker.
5. Date-only values are not converted through UTC instants.
6. An exact identity collision is reviewed; a same-name match is never enough to merge.
7. A duplicate-send warning cannot be bypassed by stale confirmation.
8. Private context is excluded from AI, analytics, logs, notifications, widgets, and support evidence by default.
9. Product analytics use content-free events, remain disabled until explicit opt-in, and require a documented purpose and retention period.
10. A capability that is unavailable in the release must not appear enabled, selectable, or successful.
11. All irreversible actions provide impact preview and explicit confirmation.
12. The user can use the core workflow offline except optional external assistance and cross-device continuity.

## 11. Ideal End-to-End Journeys

### First useful action

`Open → understand promise → add/select one person → add/confirm one moment → see reminder or compose preview → choose whether to enable notifications`

### Notification to handoff

`Open exact moment → review context → write/template/assist → edit → review recipient and duplicate warning → open messaging app → optionally confirm completion`

### Manual check-in

`Open person → write message → choose tone/template or type → review → handoff → optionally set next cadence`

### Add context after an interaction

`Open person → add short note or gift idea → choose private/assist-eligible → optionally create follow-up`

### Failure recovery

`See task-specific error → understand what did not change → retry or choose manual fallback → return to original task`

### Data exit

`Settings → Data → export or delete → review scope → authenticate if enabled → complete and verify → receive content-free confirmation`

## 12. Non-Goals and Removed Scope

The following are not part of the first product unless new evidence changes this SSOT:

- analytics dashboards, CSV reports, and relationship scores;
- bulk message operations;
- Smart Approve, VIP Approve, or Fully Auto sending modes;
- direct SMS, WhatsApp automation, inbox reading, or AccessibilityService automation;
- direct provider email delivery and SMTP configuration;
- a standalone Style Coach, Gift Advisor, Memory Vault, Chat History, Activity History, or Setup Check destination;
- complex group defaults, channel blackouts, and global scheduling matrices;
- broad contact sync, social scraping, or automatic profile enrichment;
- home widgets and launcher shortcuts before core retention is proven;
- collaboration, shared accounts, team roles, or public sharing;
- affiliate commerce, ads, sponsored gift recommendations, or data brokerage.

## 13. Quality and Release Outcomes

Before a customer beta:

- the complete primary journey is usable through a designed mobile UI, not a JSON console;
- representative users complete first value and reminder-to-handoff tasks in moderated tests;
- the privacy policy, terms, support path, data retention, and deletion behavior are published;
- Android and iOS candidates pass physical-device reminder, handoff, permission, backup, lock, and accessibility testing;
- provider-backed claims have authenticated backend, quota, abuse, cost, and incident controls;
- product-event definitions and privacy review are approved;
- crash-free sessions, startup latency, reminder reliability, and recovery targets have owners;
- no release blocker is waived by prose or manually edited evidence.

## 14. Product Discovery Gates

Before any production vertical-slice implementation:

Disposable technical feasibility spikes are allowed only when they answer a named discovery risk, are not shipped, and do not pre-commit the product architecture or scope. Otherwise, discovery and Figma experience proof come first.

1. Interview at least 15 target users across different relationship-maintenance habits.
2. Test the narrow promise against native Contacts/Calendar and general AI workarounds.
3. Prototype first value and notification-to-handoff in Figma.
4. Observe at least five users per major iteration without explaining the interface.
5. Run a concierge pilot using manual reminders and template/AI drafts.
6. Test willingness to pay with a reversible offer; do not infer it from compliments.
7. Define the smallest segment where retention and trust are measurably better than the workaround.

Failure to validate the problem means stop or reposition—not add more features.

## 15. Governance

- This file is the only normative product-scope document.
- `docs/product-reset/current-product-assessment.md` records why existing capabilities were kept, changed, or removed.
- `docs/product-reset/user-experience-and-ideal-product.md` provides detailed journey evidence and ideal flows.
- `docs/product-reset/technical-rebuild-assessment.md` defines technical reuse and rebuild boundaries.
- `docs/product-reset/product-vision-and-roadmap.md` sequences discovery and delivery.
- ADRs may choose implementation mechanisms but cannot expand product scope.
- Tests prove implemented behavior; they do not prove business value.
- A change to vision, target segment, non-goals, business rules, or committed feature scope requires an explicit SSOT decision record with evidence, owner, and date.

## 16. Open Decisions

- Is the initial segment best defined by Indian language/channel needs, by privacy sensitivity, or by a specific life stage?
- Are birthday/anniversary moments frequent enough for retention, or must opt-in check-ins be part of the first habit loop?
- Does assisted writing create enough incremental value over general AI tools to support a subscription?
- Will users maintain context, and what is the minimum context that improves outcomes?
- Is encrypted multi-device continuity required for payment, or is user-owned backup sufficient?
- Does the name “RelateAI” communicate a human outcome, or overemphasize a replaceable implementation technology?

These questions are research inputs. They must not be answered by adding code.
