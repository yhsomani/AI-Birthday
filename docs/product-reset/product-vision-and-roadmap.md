# Product Vision, Prioritized Roadmap, and Build-From-Scratch Strategy

Status: evidence-gated plan subordinate to the normative SSOT

Date: 2026-07-11

Authority: subordinate to the product scope in `../../SSOT.md`

## 1. Brutally Honest Verdict

The project should not be thrown away wholesale, but continuing the current feature roadmap would be a mistake.

The repository demonstrates unusually strong implementation work around local persistence, privacy, deterministic behavior, recovery, and message safety. It does **not** demonstrate that customers want a 28-area relationship-management product, that 141 commands map to useful workflows, that AI message generation supports a business, or that anyone will maintain enough context to make the system valuable.

The current product is also not usable by an ordinary customer: the only application interface is a temporary JSON command console. Production AI, email, cloud identity/sync, durable unattended automation, signed-device evidence, monetization, product analytics, public policy documents, and customer support operations are absent. That makes the implementation a well-tested capability laboratory—not a releasable product.

Recommendation: **reset the product, redesign the end-user experience from zero, and rebuild a narrow vertical slice while selectively reusing verified safety components.**

## 2. Vision

> Help people show up thoughtfully for the relationships they already value, at the moment it matters, with less effort and no loss of control.

The product wins if a reminder becomes an authentic action faster and more safely than the combination of Contacts, Calendar, a general AI chatbot, and manual copying.

It does not win by having more contact fields, charts, automation modes, event types, diagnostics, or provider integrations.

## 3. Strategic Choices

| Choice           | Decision                                                   | Reason                                                                                                                    |
| ---------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Initial market   | Test privacy-conscious Indian professionals/families first | English/Hindi/Hinglish and manual WhatsApp-oriented handoff provide a potentially coherent wedge, but require validation. |
| Core job         | Moment reminder to reviewed action                         | It connects a recurring trigger to a clear outcome.                                                                       |
| Product category | Relationship-moment assistant                              | “Personal CRM” creates feature expectations and competition the product should avoid.                                     |
| Sending          | Manual handoff only                                        | Maximizes trust and minimizes channel/policy infrastructure.                                                              |
| AI               | Optional assistance, never the workflow owner              | AI is replaceable; context, timing, safety, and workflow are the durable value.                                           |
| Data             | Local-first and minimum-scope                              | The trust proposition is incompatible with broad silent collection.                                                       |
| Revenue          | Subscription hypothesis without ads                        | Ads and data monetization undermine the product's reason to be trusted.                                                   |
| Platforms        | Mobile-first Android and iOS                               | The moment-to-handoff job happens on the device where messaging occurs.                                                   |
| Collaboration    | Not version one                                            | Shared data changes consent and architecture before demand is known.                                                      |

## 4. Portfolio Reset

### Commit to the first vertical slice

| Capability                                  | Outcome                                             | Decision                                |
| ------------------------------------------- | --------------------------------------------------- | --------------------------------------- |
| Contextual onboarding                       | First useful result before permission/account setup | Rebuild                                 |
| Manual person creation and selective import | Add only people who matter                          | Rebuild/simplify                        |
| Birthday, anniversary, custom moment        | Reliable recurring trigger                          | Rebuild around one Moment model         |
| Today action list                           | Immediate next action                               | Rebuild; replace dashboard              |
| Local reminders and deep links              | Return at the useful time                           | Reuse policies, rebuild experience      |
| Manual/template composition                 | Complete offline fallback                           | Reuse content rules, rebuild experience |
| Review and messaging-app handoff            | User remains sender                                 | Reuse guardrails/adapters               |
| Minimal person timeline/context             | Better future actions                               | Merge several current features          |
| Export, delete, backup, recovery            | User control and trust                              | Reuse proven lifecycle concepts         |
| Localization and accessibility              | Core usability                                      | Rebuild and validate with people        |

### Validate before adding

| Capability                         | Evidence required                                                                                           |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Authenticated assisted composition | It materially outperforms local/manual work and passes consent, quality, cost, backend, and fallback gates. |
| Keep-in-touch cadence              | Repeated voluntary use and acceptable dismiss/mute rates.                                                   |
| Encrypted multi-device continuity  | Clear retention or payment demand that user-owned backup cannot satisfy.                                    |
| AI style adaptation                | Draft acceptance improves enough to justify extra data and complexity.                                      |
| Calendar import/export             | A material share of target users cannot succeed with person moments alone.                                  |
| Review queue as primary navigation | Users accumulate enough pending work that Today becomes inadequate.                                         |
| Small home widget                  | Core retention is proven and device research shows widget demand.                                           |

### Remove from the active roadmap

- relationship health scoring;
- Analytics and CSV reporting;
- full Activity History as an end-user destination;
- standalone Setup Check/AI Doctor;
- standalone Style Coach and style-history UI;
- standalone Memory Vault, Gift Advisor, and Chat History;
- gift budgets and recommendation commerce;
- bulk approval/retry/reject;
- Smart Approve, VIP Approve, Fully Auto, and unattended sending;
- direct email provider and SMTP configuration;
- direct SMS and WhatsApp automation;
- complex group defaults, quiet-hour behavior matrices, blackouts, and per-channel automation;
- broad event taxonomies in the primary flow;
- account modes that are not actually available;
- launcher shortcuts and widgets as launch dependencies;
- operational reports exposed as product features.

## 5. Roadmap

Roadmap phases are evidence gates, not calendar promises. A phase does not start because the previous phase consumed its planned time; it starts when exit evidence is met.

### Phase 0 — Stop and learn

Objective: determine whether the narrow problem and initial segment are real.

Work:

- freeze net-new current feature development;
- interview at least 15 target users and 5 explicit non-target users;
- document current workarounds, missed-moment cost, message-writing behavior, privacy concerns, and payment language;
- test product naming and positioning without showing the existing implementation;
- define privacy-preserving product event taxonomy;
- identify one acquisition channel that can reach the segment credibly.

Exit evidence:

- repeated unaided description of the same high-cost problem;
- at least one segment with a frequent enough trigger;
- users currently spend effort on a workaround;
- assisted composition plus reminder workflow is preferred to the workaround;
- no fatal trust objection to the minimum data model.

Kill/reposition condition:

- users view native reminders plus general AI as good enough;
- important moments are too infrequent for retention;
- users will not provide even minimum context;
- no credible willingness to pay or low-cost distribution path emerges.

### Phase 1 — Figma experience proof

Objective: prove first value and reminder-to-handoff without implementation bias.

Prototype only:

- first run and manual/selective person addition;
- moment creation;
- Today action list;
- compose with manual/template/assisted choices;
- context preview;
- review, duplicate warning, and handoff;
- permission denial, provider failure, empty, and stale-link recovery;
- data export/delete explanation.

Exit evidence:

- representative users complete first value and notification-to-handoff without coaching;
- terminology is understood without a glossary;
- users distinguish a RelateAI draft from a sent message;
- permission requests are understood and deferrable;
- critical accessibility flows pass prototype review.

### Phase 2 — Narrow production vertical slice

Objective: ship the smallest complete loop to a closed cohort.

Build:

- three-destination information architecture;
- people, moments, Today, compose, review/handoff;
- reliable reminders and deep links;
- local templates and manual composition; authenticated assistance is a later technical substage only if its discovery and operating gates pass;
- minimal context/timeline;
- export, delete, backup/restore, and app lock;
- explicitly opted-in content-free product events, reliability telemetry, and cost observability;
- support, privacy, terms, and incident paths.

Do not build:

- billing, collaboration, dashboards, broad integrations, or advanced automation.

Exit evidence:

- physical-device Android and iOS tests pass;
- closed-beta users complete meaningful actions over multiple cycles;
- reminder, handoff, recovery, and provider reliability meet explicit service targets;
- private content never appears in telemetry, notifications, logs, or support evidence;
- support burden is understood.

### Phase 3 — Habit and trust beta

Objective: determine whether the product retains users and earns context.

Experiments:

- lead-time defaults;
- template versus AI entry;
- optional keep-in-touch cadence;
- context prompts shown only near an action;
- notification timing and frequency;
- backup versus multi-device continuity demand.

Exit evidence:

- activation and monthly meaningful action baselines are stable;
- a defined cohort retains without aggressive notification pressure;
- users keep or lightly edit suggestions;
- trust incidents remain below the zero-tolerance boundaries;
- qualitative feedback explains why the product is better than the workaround.

### Phase 4 — Monetization proof

Objective: prove a sustainable exchange of value.

Work:

- run pricing interviews and offer tests;
- test a paid assisted-writing/continuity bundle without restricting export, deletion, templates, or accessibility;
- model provider cost, support cost, store fees, refunds, and taxes;
- build billing only after a credible conversion signal.

Exit evidence:

- users choose a real paid offer, not merely express interest;
- gross margin remains viable under realistic generation and support usage;
- free users still receive the complete trust-preserving core loop.

### Phase 5 — Selective expansion

Only consider capabilities that address measured failure or demand. Each addition must displace another roadmap item and declare a removal condition.

Possible candidates: keep-in-touch, encrypted continuity, calendar import, small widget, or a review queue. Analytics dashboards, bulk sending, social scraping, and unattended messaging remain excluded.

## 6. Build-From-Scratch Strategy

### 6.1 What “rebuild” means

Rebuild means a new product shell, navigation model, use-case API, state ownership model, and reduced data model. It does not mean discarding every verified algorithm.

The current application should be treated as:

- a source of tested policy behavior;
- a safety regression suite;
- a migration source if existing customer data ever exists;
- a capability lab kept outside the new user-facing architecture.

It should not dictate screen structure, feature count, terminology, or internal boundaries.

### 6.2 Selective reuse

Candidate reuse after independent review:

- date recurrence and time-zone rules;
- duplicate-send and stale-confirmation guards;
- strict input/body/context validation;
- privacy-safe notification payloads and manual handoff adapters;
- bounded provider transport and short-lived session contract;
- encrypted backup format concepts;
- journaled clear/restore and fail-closed recovery rules;
- redacted operational issue model;
- locale/date/currency utilities;
- release evidence and native permission policy checks.

Rebuild rather than carry forward:

- the temporary JSON application shell;
- 141-command product surface;
- monolithic AppState and reducer;
- 5,000+ line command runtime and 1,800+ line parser;
- feature-specific confirmation/session mechanics duplicated in one coordinator;
- current navigation vocabulary;
- broad settings and automation model;
- custom feature organization based on the previous 28-area FSSOT;
- documentation that treats implementation breadth as product scope.

Replace unless a fresh architecture review proves otherwise:

- the bespoke encrypted entity-file repository as the long-term default; prefer a mature encrypted relational store with explicit migrations and transactional constraints;
- client-visible provider configuration without a complete backend/session/entitlement path;
- broad `READ_CONTACTS` import if minimum-scope contact pickers can support the validated workflow.

### 6.3 Construction sequence

1. After discovery and Figma exit evidence passes, freeze the current domain model as reference; do not add adapters for the new UI.
2. Define the narrow data vocabulary: Person, Moment, ContextEntry, Draft, ActionRecord, ReminderPreference, and DataControlState.
3. Define fewer than 20 user-intent use cases around complete tasks, not CRUD commands.
4. Build one end-to-end vertical slice with real persistence and native reminder/handoff behavior.
5. Add migration adapters only for data that exists and is retained in the new SSOT.
6. Run old policy tests against extracted reusable packages where semantics remain valid.
7. Delete or archive unretained capability code after migration and rollback windows close.
8. Keep the temporary harness as a development-only diagnostic tool if it remains cheaper than dedicated fixtures; exclude it from the customer bundle.

### 6.4 Migration approach

There is no evidence of production users or irreplaceable installed data in the repository, so a clean cut is preferable to a long-running strangler migration.

If real user data exists outside the repository:

- inventory live schema versions and active installs before implementation;
- create a read-only migration extractor;
- map only retained entities into the new model;
- preview data loss or consolidation to the user;
- verify counts and checksums before switching;
- preserve a time-limited encrypted rollback artifact;
- never silently map removed automation or scoring settings into new behavior.

### 6.5 Rebuild exit criteria

The old product path can be removed only when:

- the narrow primary journeys pass usability and device tests;
- retained data migrates with verified integrity;
- safety invariants have equivalent or stronger tests;
- external provider and store claims have primary evidence;
- rollback and support procedures are proven;
- no active user depends on a removed capability without explicit communication/export.

## 7. Measurement Plan

Product analytics remains disabled until explicit opt-in. For consenting users, the minimum content-free events are:

- onboarding started/completed/abandoned stage;
- person added manually or selectively imported;
- moment saved;
- reminder scheduled/opened/snoozed/dismissed;
- compose method selected: manual/template/assisted;
- assisted request succeeded/fell back/failed and latency/cost band;
- draft reviewed, lightly edited/heavily edited/rejected;
- handoff opened, fallback used, completion confirmed/declined;
- export/restore/delete started/completed/failed;
- permission prompt shown/granted/denied after rationale;
- task-specific recovery succeeded/failed.

Never collect names, routes, dates, relationship labels, notes, message text, AI prompts/responses, backup metadata, or stable contact identifiers in product analytics.

Every event requires purpose, retention, owner, denominator, privacy classification, and deletion behavior.

## 8. Major Risks

| Risk                                  | Why it matters                      | Response                                                                              |
| ------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------- |
| Native tools are “good enough”        | Removes reminder differentiation    | Test the complete reminder-to-authentic-action outcome, not reminders alone.          |
| General AI commoditizes writing       | AI feature cannot sustain pricing   | Differentiate on selected context, timing, trust, and handoff; keep templates strong. |
| Moments are too infrequent            | Weak retention and subscription     | Validate opt-in check-ins; do not manufacture notification volume.                    |
| Users will not maintain context       | Personalization quality stalls      | Ask only near an action and prove immediate benefit.                                  |
| Contact permission policy tightens    | Broad import may become unshippable | Design selective picker/manual flows first.                                           |
| Local-only data is lost or fragmented | Trust and retention suffer          | Provide user-owned backup first; validate continuity demand.                          |
| AI leaks sensitive context            | Existential trust incident          | Context preview, minimization, server isolation, redaction, incident controls.        |
| Feature creep returns                 | Recreates current complexity        | SSOT authority, evidence gates, WIP limits, and explicit non-goals.                   |

## 9. Team Operating Model

- Product owns problem evidence and outcome metrics.
- Design owns observed task completion, not screen count.
- Engineering owns reliability, privacy, cost, and reversible delivery.
- QA owns journey-level risk and evidence coverage, not only command correctness.
- Security/privacy review begins at discovery, not release.
- Support participates before beta so failures and terminology are understandable.
- Every roadmap item declares hypothesis, owner, measure, guardrail, and kill condition.

## 10. Final Recommendation

Pause expansion immediately. Validate the narrow relationship-moment thesis, design it in Figma without reference to the temporary console, and build one production vertical slice. Preserve verified safety logic only where it serves the new model. Delete the rest when the new path and any required migration are proven.

The correct next investment is discovery and experience proof—not another feature, screen, report, provider, or automation mode.
