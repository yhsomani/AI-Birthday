# Product Reset: User Experience and Ideal Product

Status: Clean-slate product recommendation

Perspective: End user first, business first

Date: 2026-07-11

## Executive Decision

RelateAI should be rebuilt as a product experience from zero.

The repository contains a broad and often thoughtful set of functional rules, but the current executable interface is a JSON command console. A normal user cannot discover, understand, or complete the product's core jobs through it. The intended experience is spread across 28 indexed feature areas (including 11A and 11B), 17 conceptual routes, and 141 command types. Several promoted journeys depend on capabilities that the release explicitly marks unavailable, including Google sync, authenticated AI, provider email, and durable unattended automation.

This is not a visual-design problem. It is a product-shape problem.

The reset should preserve only outcomes that remain valid after user research: privacy by default, review before communication leaves the app, safe duplicate prevention, user-initiated permissions, reliable reminders, recoverable local data, and accessible localized operation. Existing screens, feature names, navigation, setup sequences, scores, dashboards, provider concepts, and automation modes should have no presumption of survival.

The ideal product is:

> A private, review-first relationship assistant that helps people remember meaningful moments, prepare thoughtful messages, and follow through without taking control of their relationships.

The product is not a CRM, a mass-messaging tool, a relationship-scoring system, a provider configuration console, or an autonomous agent that communicates on the user's behalf.

## Evidence and Scope

This analysis is grounded in the current repository rather than inferred from feature names alone:

- The visible product is one command input, one secret input, redacted output, operations, and issues in [MinimalFunctionalShell.tsx](../../src/app/MinimalFunctionalShell.tsx).
- External routes update internal navigation state, but no feature destination is rendered by [App.tsx](../../src/App.tsx).
- The only discoverability mechanism is an exhaustive 141-command vocabulary in [commandCatalog.ts](../../src/application/commandCatalog.ts).
- The aspirational product spans 28 indexed feature areas in [feature-fssot.md](../feature-fssot.md).
- Existing prioritization already recognizes scope inflation and trust risks in [feature-roadmap-analysis.md](../feature-roadmap-analysis.md).
- The onboarding model has four goals and nine ordered steps in [onboarding.ts](../../src/domain/onboarding.ts).
- Google sync, authenticated AI, provider email, and unattended automation are explicitly unavailable in [productAvailability.ts](../../src/config/productAvailability.ts).
- Home ranks message recovery, reviews, event preparation, check-ins, backup, setup, and enrichment in [homePlanner.ts](../../src/domain/homePlanner.ts).
- Relationship and personalization percentages are derived from app-held data in [relationshipHealth.ts](../../src/domain/relationshipHealth.ts), [contactEnrichment.ts](../../src/domain/contactEnrichment.ts), and [analytics.ts](../../src/domain/analytics.ts).
- The intended route hierarchy is encoded in [navigationState.ts](../../src/navigation/navigationState.ts), even though those routes do not have active screens.
- The privacy and channel constraints are documented in [privacy-and-permissions.md](../security/privacy-and-permissions.md).

Scores and recommendations below evaluate the current end-user product, not the amount or quality of underlying code.

## Business Reality Check

### The underlying need is plausible but unvalidated

Forgetting birthdays, anniversaries, follow-ups, personal details, or promises to reconnect—and facing a blank page when trying to write thoughtfully—are credible needs. This repository contains no customer evidence proving their frequency, urgency, or willingness to pay. A product that combines timely preparation with trusted personal context could create recurring value; discovery must establish whether it actually does.

### The current proposition is not yet defensible

- Date reminders alone compete with the phone calendar, contacts app, social platforms, and messaging apps.
- Generic AI writing competes with general-purpose assistants that require no relationship database.
- A long setup flow destroys value before the product has earned trust.
- More automation does not create a moat if users do not trust the output or the send behavior.
- Analytics, gift budgets, diagnostics, and provider configuration do not compensate for a weak core loop.

The defensible product is the loop, not the model: remember the right moment, surface just enough trusted context, help the user prepare, preserve final control, and learn from explicit user choices over time.

### Business hypotheses that must be validated

Before scaling implementation, research must prove that target users:

1. Have enough meaningful relationships or occasions to return at least monthly.
2. Will entrust the app with selected dates and context when privacy is clearly explained.
3. Find context-aware preparation materially better than a calendar reminder plus a general AI tool.
4. Prefer review-first assistance over autonomous sending.
5. Will pay for privacy-preserving assisted writing and encrypted continuity—not for privacy rights or reminders alone.

If these hypotheses fail in interviews and prototype tests, the business should narrow to a lightweight occasion-preparation tool or stop rather than expand the feature set.

## Target Users, Jobs, and Pain Points

### Needs within the canonical primary persona

The SSOT defines one primary persona: the thoughtful but time-poor connector. The first market wedge is a hypothesis around urban Indian professionals and globally distributed Indian families using English, Hindi, or Hinglish. The rows below are overlapping need patterns within that persona, not four separate target segments.

| Persona                                 | Situation                                                                   | Core job                                                             | What earns trust                                                  | What causes abandonment                                 |
| --------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------- |
| Busy relationship maintainer            | Has family, close friends, and work relationships competing for attention   | “Help me remember and act before an important moment passes.”        | Reliable reminders, fast preparation, no guilt                    | Long setup, noisy nudges, judgmental scores             |
| Thoughtful but time-constrained writer  | Cares about wording but often faces a blank page                            | “Help me say something specific that still sounds like me.”          | Editable suggestions, visible context, local fallback             | Generic AI text, lost edits, forced provider setup      |
| Privacy-first organizer                 | Wants continuity but is cautious with contacts, notes, and message content  | “Let me keep useful context without surrendering control of it.”     | On-device defaults, purpose-specific consent, clear export/delete | Hidden uploads, vague AI usage, unrecoverable data      |
| Long-distance or multilingual connector | Maintains relationships across languages, time zones, and cultural contexts | “Help me remember the right time and communicate in the right tone.” | Contact-level language and timing, reviewability                  | Wrong-language drafts, ambiguous dates, rigid templates |

### Secondary persona

The family relationship organizer tracks dates and context across generations or locations but still uses a single-user product in version one. Shared household coordination is a later discovery opportunity, not a reason to compromise consent, identity, or private notes.

### Explicit non-personas

The product should not target sales teams, campaign senders, social-media growth users, inbox scrapers, or anyone seeking autonomous bulk outreach. Serving those users would turn a trust-centered consumer assistant into a CRM or messaging automation product.

### Jobs to be done

1. Capture the few people and moments I genuinely care about.
2. Know what deserves attention today or soon.
3. Prepare early enough to do more than send a last-minute generic greeting.
4. Recall useful context without searching across apps or notes.
5. Draft a message quickly while retaining my own voice and judgment.
6. Avoid duplicate, awkward, mistimed, or wrong-channel communication.
7. Follow up after a meaningful moment when I choose to.
8. Keep my data private, portable, and recoverable.

### Current user pain points the product should solve

- Important dates are fragmented across memory, contacts, calendars, and conversations.
- Existing reminders say that an event exists but do not help the user prepare.
- General AI lacks trusted relationship context and can sound generic or inappropriate.
- Personal notes are useful but sensitive.
- Users cannot always know whether a manual handoff was actually sent or delivered.
- Notification overload and “neglected relationship” language can create guilt rather than help.
- Data loss is especially painful because relationship context is difficult to recreate.

## Current Feature Reality and Value Assessment

The only per-feature numeric scores are in [current-product-assessment.md](current-product-assessment.md), where all 68 capabilities use one rubric. This thematic view deliberately does not rescore them.

| Feature cluster                            | Current end-user reality                                                           | Portfolio decision                                                       |
| ------------------------------------------ | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Entry, navigation, links, shortcuts        | Console-only routes do not form a usable customer journey.                         | Rebuild real destinations; postpone shortcuts and widget.                |
| Onboarding, account, setup                 | Configuration precedes value and includes unavailable capabilities.                | Rebuild from first value; merge recovery into context.                   |
| Privacy and security                       | Strong rules exist behind inaccessible controls and unverified device behavior.    | Keep principles; redesign controls and explanations.                     |
| Home/relationship planner                  | An unvalidated priority engine has no customer surface.                            | Rebuild as focused Today actions.                                        |
| People, import, profile                    | Useful record logic is buried behind JSON and an overloaded profile model.         | Simplify around selected people and progressive context.                 |
| Classification and health scoring          | Numeric judgments are arbitrary, duplicated, and potentially guilt-inducing.       | Remove scores; retain factual, explainable task signals only.            |
| Moments, dates, preparation, reminders     | Strong recurrence logic lacks an end-to-end customer flow and device proof.        | Rebuild as the core preparation journey.                                 |
| AI generation, manual composer, templates  | Three mechanisms are fragmented; production AI is unavailable.                     | Merge into one honest Compose experience with local fallback.            |
| Preview, message queue, approval, recovery | Safety states are rigorous but system-centric and invisible to ordinary users.     | Merge into contextual Review with humane recovery.                       |
| Scheduling and automation modes            | Names imply automation that the shipping composition cannot perform.               | Remove unattended modes; simplify to reminders and preparation.          |
| Manual SMS/WhatsApp/email handoff          | Review-first handoff is strategically useful but lacks a usable, device-proven UI. | Keep safety rules; rebuild the flow.                                     |
| Notifications                              | Permission and reconciliation logic exists without contextual customer setup.      | Request in context and measure reliability.                              |
| Widget and launcher shortcuts              | Optional native surfaces precede a validated core app.                             | Postpone until retained use proves demand.                               |
| Analytics and CSV reports                  | Reports derive questionable relationship and delivery measures.                    | Remove; consider only factual reflection after evidence.                 |
| Activity History and Setup Check           | Operator detail is modeled as customer-facing feature breadth.                     | Keep content-free support evidence; show fixes in context.               |
| Style Coach                                | Heuristic training adds sensitive setup before drafting value is proven.           | Merge minimal writing preferences into Compose.                          |
| Memory Vault, Gift Advisor, Chat History   | Separate destinations fragment one person's context and overstate known history.   | Merge useful notes and marked actions into one person timeline.          |
| Backup, restore, clear, recovery           | Strong safety logic is unusable through the console and lacks device proof.        | Preserve guarantees; rebuild the experience and independently review it. |
| Localization and accessibility             | String contracts exist, but no real flow can be accepted.                          | Make both cross-cutting release gates for the reduced product.           |

## Current Journey Maps and Findings

There are two “current” products to distinguish:

1. The executable product, where every journey begins by editing JSON, obtaining record identifiers and confirmation tokens from prior output, and running commands.
2. The aspirational product described in documents and domain contracts, which assumes screens that do not currently exist.

The journey maps below combine both so that functional intent is not mistaken for an available experience.

### 1. Onboarding

Current intended flow:

`Choose one of four goals → traverse intro/account/contacts/notifications/AI/style/channels/backup → reach finish → Home`

Current executable flow:

`system.catalog → onboarding.inspect → onboarding.set-goal → onboarding.set-step/advance/skip repeatedly → separate commands for every setup dependency → onboarding.complete`

Findings:

- There is no consumer onboarding UI.
- Nine ordered steps are too many before first value, even if several are optional.
- “AI wishes” is offered as a goal, but its required AI step needs a ready authenticated provider that the checked-in product cannot supply. This is a dead end, not progressive setup.
- “Full setup” front-loads notifications, AI, channels, style, and backup before the product has earned trust.
- Account choice is artificial because only Local mode is available.
- Users must visit or skip steps until the finish state even when their real job is simply to save one birthday.
- “Manual relationship manager” describes product architecture rather than a user aspiration.

Business impact: poor activation, permission refusal, inflated support burden, and a high chance that users leave before creating one useful reminder.

### 2. Relationship setup, contact import, and profile enrichment

Current intended flow:

`Choose device/Google/manual source → grant access → import → preview conflicts → apply → review contact → configure group, channel, tone, cadence, automation, quiet hours, VIP/DND → add enrichment`

Current executable flow:

`contacts.import/import-preview/import-apply or contacts.add → query IDs → inspect → many preference commands → enrichment.inspect/answer`

Findings:

- Google sync is promised in the feature specification but explicitly unavailable in the release.
- Import has sensible preview and conflict concepts, but they are inaccessible without command knowledge.
- The profile asks for far more than is needed to create first value: group, tone array, channel, cadence, automation, quiet-hour behavior, DND, VIP, notes, gift budget, and route verification.
- Numeric personalization coverage rewards disclosure rather than helping the user complete a task.
- Importing many contacts can immediately create check-in debt because a missing last-contact date is treated as due.
- Classification guesses and “health” scores can mislabel intimate relationships from sparse keywords.
- There is no clear distinction between “stored privately” and “allowed for AI” at the moment context is entered; category choice carries too much privacy responsibility.

Business impact: importing becomes a data-cleaning project. Users do work for the app instead of receiving value from it.

### 3. Occasion planning

Current intended flow:

`Discover/add event → resolve date conflict → open event → complete date/context/message/gift/channel/reminder checklist → generate/review message`

Current executable flow:

`events.query/add/import/calendar commands → preview/apply or merge tokens → events.preparation.inspect/toggle → reminders.reconcile`

Findings:

- Eight event types appear in the model; several—Revival, Follow-up, Holiday, Graduation, Work anniversary—dilute the birthday/anniversary/custom core.
- “Revival” is unclear and potentially judgmental. Follow-up is an action reminder, not an occasion type.
- A universal preparation checklist risks turning a thoughtful gesture into project administration.
- Gift, channel, and reminder tasks are not relevant to every moment.
- The documented month view and event cards do not exist.
- Conflict and recurrence logic may be sound, but users cannot see why two dates differ or what a merge will preserve.
- Device-calendar export is a secondary integration and should not compete with creating the first trusted moment.

Business impact: the core reason to use the product is buried under taxonomy and operational steps.

### 4. Draft, review, and send

Current intended flow:

`Choose AI/manual/template path → select reason/tone/variant → inspect context → edit → regenerate/test → approve → schedule or open channel → return → confirm sent`

Current executable flow:

`composer/ai/templates command → messages.query/preview/edit/set-channel/select-variant → acknowledge duplicate → approve → handoff.open → handoff.confirm`

Findings:

- One user job is split across AI Message Generation, Manual Composer, Template Library, Wish Preview, Messages Inbox, Style Coach, channel setup, and handoff recovery.
- “Wish” narrows the product to celebrations while the composer supports thanks, apology, check-in, and follow-up.
- The release cannot provide authenticated AI, so a primary path falls back to templates after setup-oriented language suggests otherwise.
- Nine message statuses expose internal workflow complexity. “Delivery pending” and “Delivery unknown” are not meaningful when a manual destination app owns the send.
- Manual handoff confirmation is correctly explicit, but “Sent” means user-confirmed—not verified delivery. Current analytics can overstate this as delivery success.
- Variant names such as short/standard/warm mix length and tone dimensions.
- Review-next, route tests, approval expiry, bulk tools, regeneration feedback, duplicate acknowledgements, and scheduling confirmations create confirmation fatigue when exposed together.
- There is no visible editor, preserved draft surface, or contextual recovery action.

Business impact: the differentiating job—help me write and act—has the highest conceptual fragmentation.

### 5. Reminders and relationship check-ins

Current intended flow:

`Set global/contact cadence → receive due reminder → write, snooze, or mark contacted elsewhere → update Home and history`

Current executable flow:

`settings/contact preference commands → checkins.query → checkins.snooze or mark-contacted → composer commands`

Findings:

- Every active contact receives a cadence, and a contact with no last-contact date is immediately due. A large import can therefore produce a wall of guilt.
- “Overdue” is calculated from app data, not the reality of a relationship. The user may have talked elsewhere without recording it.
- Health labels and “neglected contacts” amplify an unreliable inference.
- Check-ins, occasion reminders, approval alerts, backup reminders, setup blockers, recovery alerts, and revival suggestions compete for notification attention.
- Notification permission is positioned as an onboarding step rather than requested after the user creates a reminder worth receiving.
- In-app fallback is conceptually present, but there is no visible Today surface.

Business impact: notification fatigue and moralizing language can make users disable the product entirely.

### 6. Memories, gifts, and prior messages

Current intended flow:

`Open contact → enter Memory Vault, Gift Advisor, or Chat History → add/search/edit records → return to draft or event`

Current executable flow:

`memories.*, gifts.*, timeline.query, or chat.query commands using a contact ID`

Findings:

- One relationship history is fragmented across four concepts: profile notes, Memory Vault, Gift Advisor, and Chat History/Timeline.
- “Chat History” is misleading because the app only knows RelateAI-recorded outgoing messages, not the conversation.
- “Memory Vault” sounds like a destination or archive when the user normally needs context inside a person or moment.
- Gift budgets and feedback accounting are niche and too prominent for the core product.
- A user can accidentally classify sensitive content as AI-eligible; privacy should be explicit per item and private by default.
- The best moment to ask for context is while preparing for a person, not in a separate maintenance feature.

Business impact: useful context—the potential compounding advantage of the product—is made expensive to capture and hard to revisit.

### 7. Backup, privacy, and recovery

Current intended flow:

`Open Settings/Backup → enter strong passphrase → choose destination → export → later select file → enter passphrase → preview replacement → confirm atomic restore`

Current executable flow:

`backup.export/select-file/restore-preview-selected/restore-confirm using $SECURE_INPUT → data.recover or confirmed corrupt-storage clear when needed`

Findings:

- The underlying encrypted, preview-before-restore, fail-closed, and atomic-recovery rules are strong.
- The current interaction is unusable for a consumer and requires knowledge of a secure placeholder, selection tokens, and recovery commands.
- User-held passphrases create a predictable dead end when forgotten; the product needs clear recovery expectations before export.
- Storage health, journal recovery, reconciliation, and cryptographic erasure are implementation details, not destinations.
- Privacy, permission status, biometric lock, backup, export, delete, sign-out, and diagnostics are spread across multiple conceptual features.
- Backup appears in onboarding before most users have data worth protecting, yet it may be forgotten once data becomes valuable.

Business impact: strong engineering guarantees do not become user trust unless they are understandable and recoverable.

### 8. AI and email provider setup

Current intended flow:

`Enable AI/email → configure endpoint/sender → obtain authenticated provider session → run readiness test → use provider or recover through Setup Check`

Current reality:

`Public endpoint variables are build configuration → no provider-session issuer is bundled → Setup Check reports missing readiness → local templates/manual mail handoff remain`

Findings:

- There is no viable end-user provider setup journey in the checked-in product.
- Endpoint safety, session issuance, quotas, and provider observability are product operations, not consumer settings.
- Asking users to understand endpoints, providers, readiness, authentication sessions, or SMTP-like concepts is the wrong abstraction.
- A setup checker cannot repair a backend the product does not ship.
- AI is described as a core value while remaining unavailable; this damages credibility.
- Provider-backed email is secondary to mail-app handoff and should not exist in the main setup path.

Business impact: the product advertises a core capability it cannot fulfill and transfers infrastructure complexity to the user.

## Cross-Journey UX Failures

### Dead ends

- Selecting the AI onboarding goal without an authenticated provider path.
- Choosing or reading about Google sync when only Local mode exists.
- Opening a deep link, widget, shortcut, Home action, or Setup action that changes internal navigation but renders no destination.
- Trying to resolve storage, permission, or provider failures without knowing the exact recovery command.
- Reaching “email provider setup” without a shipped account/session service.

### Discoverability and effort

- The catalog is exhaustive for testing, not usable for humans.
- Record IDs, preview tokens, confirmation tokens, and JSON schemas are required to make progress.
- Core actions are scattered across separate feature pillars instead of organized around a moment or person.
- Secondary tools compete with the primary value proposition.
- Setup is presented as a product area rather than embedded guidance.

### Terminology that should not survive unchanged

| Current term                        | Problem                                                  | Preferred language                                            |
| ----------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------- |
| Contact                             | Database-like when used as the primary relationship noun | Person or People                                              |
| Event                               | Generic and technical                                    | Moment; use Birthday, Anniversary, or Reminder in context     |
| Wish Preview                        | Too celebration-specific                                 | Review message                                                |
| Memory Vault                        | Overstated separate destination                          | Notes or context within a person's timeline                   |
| Chat History                        | Not an actual chat history                               | Sent messages or timeline                                     |
| Relationship health / neglected     | Judgmental and based on incomplete data                  | Optional check-in due, context missing, or no upcoming moment |
| Personalization score               | Incentivizes data entry for its own sake                 | “Enough context to draft” plus optional suggestions           |
| Smart approve / VIP approve         | Opaque automation semantics                              | Remove; state the exact action the app may take               |
| DND                                 | Device jargon and ambiguous scope                        | Pause reminders and draft suggestions for this person         |
| Blackout                            | Operational language                                     | Dates when reminders or sending should pause, only if needed  |
| Provider endpoint/session/readiness | Infrastructure jargon                                    | Never expose in the consumer product                          |
| Setup Check                         | Makes the app feel fragile                               | Contextual “Fix this” guidance; diagnostics under Help        |
| Revival                             | Unclear and potentially insensitive                      | Remove; use an optional check-in reminder                     |

### Validation and recovery

The code contains many useful bounds and preview/confirm rules, but they are not an experience. Ideal validation must be inline, specific, and placed before the action it protects. Recovery must preserve entered text, show one recommended action, and never require internal error codes. Confirmation should be reserved for sending, destructive replacement, data deletion, or a material automation change—not every state transition.

### Trust risks

- Numeric health scores imply knowledge the product does not have.
- “Delivery success” is not credible for user-confirmed manual handoff.
- A large import can create unearned overdue states.
- AI/private-context eligibility is too dependent on users understanding categories.
- Promoting unavailable provider capabilities creates a bait-and-switch experience.

## Ideal Product Definition

### Product promise

RelateAI helps the user answer three questions:

1. Who or what deserves my attention soon?
2. What would make this interaction thoughtful?
3. What is the smallest safe action I can take now?

### Core loop

`Capture a person or moment → prepare with trusted context → receive a timely nudge → compose and review → act in the user's chosen app → optionally follow up`

Every first-class feature must shorten or strengthen this loop. Features that mainly expose app maintenance, provider state, or internal records should be removed or hidden.

### Product principles

1. First value before setup. A user should save one person and one meaningful moment before being asked to configure optional capabilities.
2. Review first. The app may prepare and remind; it does not communicate autonomously in the initial product.
3. Context is earned and purpose-limited. Store the minimum, explain AI use, and make optional context removable.
4. No relationship judgment. Show observable tasks, not moralized scores.
5. One job, one flow. Manual writing, templates, and AI belong in one composer.
6. Progressive disclosure. Advanced preferences appear only when the user encounters the need.
7. Honest capability. If the product backend does not exist, the feature is absent—not “needs setup.”
8. Offline usefulness. People, moments, notes, local templates, review, and backup must not depend on AI.
9. Errors point forward. Show impact, preserve work, and offer one primary recovery action.
10. Accessibility and localization are product behavior, not polish.

## Ideal Information Architecture

The product needs three primary destinations and no generic More tab. Compose and review are contextual tasks; a dedicated Review queue should be added only if beta behavior proves that users accumulate enough draft work to justify it.

```text
Today
├── Next important action
├── Upcoming moments
├── Drafts to review
└── Opt-in check-ins

Moments
├── Calendar and upcoming list
├── Add/import moment
└── Moment preparation

People
├── Search and selected filters
├── Person overview
├── Timeline: moments, notes, gifts, marked-completed actions
└── Person preferences

Profile menu
├── Writing and language preferences
├── Notifications
├── Privacy and data
├── Backup and restore
└── Help
```

### Navigation rules

- Today, Moments, and People are persistent bottom-navigation destinations.
- Drafts needing attention appear in Today; compose and review open from a person, moment, or Today item rather than a permanent tab.
- Settings opens from a profile/avatar action, not a fifth “More” destination.
- Add person, add moment, and write message are available through one context-aware create action.
- Notes, gifts, and sent messages are sections or filters in a person's timeline, not standalone destinations.
- Setup and diagnostics appear in context. A deeper diagnostic view exists only under Help.
- A notification or deep link opens the exact person, moment, or message after unlock. If the record is gone, the app explains that and returns to the owning list.
- Navigation never performs approve, send, import, restore, or delete as a side effect.

## Ideal Today Dashboard

Today should not be a statistics dashboard. It should be an action surface with at most five meaningful sections:

1. Next action: one clear, time-sensitive recommendation with an explanation.
2. Coming up: the next few birthdays, anniversaries, and custom reminders.
3. Ready for review: drafts the user asked the app to prepare.
4. Check in: only people for whom the user explicitly enabled a cadence.
5. Quick capture: add a person, add a moment, or write a message.

Rules:

- Message failures and events happening soon outrank optional enrichment.
- Backup or permission warnings appear only when relevant to the current action or materially risky.
- No relationship-health percentage, neglected-person count, data-coverage chart, or setup progress ring.
- Empty state leads directly to adding the first person or moment.
- Completing an action removes it immediately and reveals the next one.

## Ideal End-to-End Journeys

### 1. First run and activation

1. Show one sentence of value and one sentence of privacy: remember moments, prepare thoughtful messages, keep control; data stays on device unless the user chooses an online feature.
2. Offer “Add someone” and “Import selected people.” Do not ask for account, AI, channel, style, biometric, or backup configuration.
3. For import, explain contact access immediately before the OS prompt and allow manual entry after denial.
4. Ask the user to confirm or add one meaningful date for one person.
5. Ask when they want to be reminded, then request notification permission with that concrete reminder as the rationale.
6. Land on Today showing the saved moment and one useful next action, such as add context or prepare a message.
7. Offer AI only when the user first asks for writing help. Local templates remain available without setup.

Success outcome: a first person and actionable moment are saved in under three minutes without completing optional setup.

### 2. Add or import people

1. Choose Manual or Import selected contacts.
2. Import shows a privacy explanation, then a selectable preview rather than copying the entire address book by default.
3. Normalize names and routes in the background; show discovered birthdays separately for confirmation.
4. Group likely duplicates with a plain comparison: “These may be the same person.” Never silently merge ambiguous records.
5. Save minimal fields: name plus either a moment or a way to contact them. Relationship label is optional.
6. After success, offer one lightweight prompt: “What would make messages to Asha feel personal?” Skip remains prominent.
7. Additional language, timing, tone, check-in, and channel preferences appear later in context.

Success outcome: import feels like selecting people to care about, not cleaning a database.

### 3. Plan a meaningful moment

1. Open Moments in list or calendar form.
2. Default creation choices are Birthday, Anniversary, and Custom reminder. Advanced types appear only after “More types.”
3. Confirm the local date, recurrence, time zone when relevant, and source confidence.
4. If a conflict exists, compare the two dates and their sources; choose one, keep both with labels, or defer review.
5. The moment detail shows a dynamic preparation plan based on time remaining and user intent:
   - confirm uncertain date;
   - add optional context;
   - prepare a message;
   - consider a gift only for relevant moments;
   - choose a reminder.
6. Tasks already satisfied by existing notes, drafts, or reminders disappear rather than remaining as checked bureaucracy.
7. As the date approaches, Today and notifications surface the smallest unfinished action.

Success outcome: the user knows what to do next without managing a universal checklist.

### 4. Compose, review, and act

1. Start from a person, moment, Today action, or Compose.
2. Choose the intent: celebrate, check in, thank, congratulate, apologize, follow up, or custom.
3. Show one composer with three optional sources of help:
   - Start from scratch.
   - Use a local template.
   - Suggest with AI, when genuinely available.
4. Show compact context chips such as relationship, language, selected notes, prior sent wording, and “avoid” guidance. The user can exclude optional items before AI runs.
5. Return at most three clearly differentiated suggestions. Preserve all manual edits when switching help modes.
6. Review catches blank/short text, wrong language, channel length, duplicate wording, stale person/moment data, and sensitive-context mistakes inline.
7. Choose “Open in Messages,” “Open in WhatsApp,” “Open in Mail,” “Copy,” or “Save for later.” The initial product does not silently send.
8. On return, ask “Did you send it?” with Yes, Not yet, and Don't ask again for this draft. Record “Marked sent by you,” not verified delivery.
9. Offer an optional follow-up reminder only after confirmed action.

Success outcome: one coherent flow turns intent into an edited, trusted message without provider jargon or status management.

### 5. Reminders and check-ins

1. Occasion reminders are configured per moment with sensible presets such as one week before and on the day.
2. Check-in reminders are off by default and enabled only for selected people or an explicitly chosen circle.
3. The user chooses a human interval such as monthly, every few months, or a custom date. The app never declares a newly imported person overdue.
4. A due card offers Write a message, Snooze, or I already reached out.
5. “I already reached out” records only the user's statement; it does not imply channel access or reply knowledge.
6. Notification settings provide quiet delivery windows and category-level controls. Sending blackouts do not exist until genuine scheduled sending exists.
7. When notification permission is denied, Today remains the honest fallback and the app does not claim it can alert outside the app.

Success outcome: reminders feel chosen and useful, not like an automated judgment of relationship quality.

### 6. Capture context and gifts

1. The person's overview contains a chronological timeline of moments, user-confirmed sends, notes, and gifts.
2. Add note is available inline while preparing a message or moment.
3. Every note is private app data by default. A separate, explicit “Use this for message suggestions” switch controls AI/template eligibility.
4. Prompts are contextual and optional: what to mention, what to avoid, preferred language, or a recent life update.
5. Gift planning appears only near gift-relevant moments or from the person's timeline.
6. The initial gift feature records ideas and past gifts to avoid repetition. Budgets, feedback scoring, and AI shopping remain later experiments.
7. Prior messages appear as “Messages you marked sent,” not a fake chat transcript.

Success outcome: useful context accumulates naturally inside real tasks instead of requiring maintenance of separate vaults.

### 7. Privacy, backup, restore, and recovery

1. Privacy & Data explains, in plain language, what is on device, what can be used for suggestions, what leaves the device, and which permissions are active.
2. Biometric lock is offered after sensitive notes exist, not as a prerequisite to activation.
3. Backup becomes a visible recommendation after the user has meaningful data or before deletion/restore/device migration.
4. Export explains that the file is encrypted, the passphrase is not recoverable, and losing both device and passphrase loses the backup.
5. Restore begins with file selection and passphrase, then shows a human preview: people, moments, notes, messages, backup date, and whether current data will be replaced.
6. Replacement requires one clear confirmation. Restore is atomic; failure preserves current data and offers retry or a safe support report.
7. Interrupted cleanup or restore is described as “Finish recovering your data,” with Resume and Learn more. Journal phases, reconciliation, and storage keys remain hidden.
8. Delete all local data requires re-authentication when lock is enabled, a summary of consequences, backup option, and typed or platform confirmation.

Longer-term, test optional end-to-end encrypted account backup or user-owned cloud storage to reduce manual-file loss. Do not claim this until recovery keys, device migration, deletion, and threat model are proven.

### 8. AI and provider experience

1. The consumer chooses whether to use online suggestions, not which endpoint to configure.
2. The product owns authentication, provider reliability, quotas, abuse controls, and failover.
3. Before first AI use, show what types of context may be sent and let the user continue with local templates instead.
4. Each generation shows the selected context and excludes private/unselected notes.
5. If AI is unavailable, preserve the draft and offer local templates immediately. Do not route users to endpoint diagnostics.
6. Email provider delivery is absent until the business ships a turnkey, authenticated connection. Mail-app handoff remains simple and sufficient for the initial product.
7. If the business cannot operate a reliable provider service, remove AI from positioning rather than exposing a broken setup journey.

Success outcome: AI feels like optional writing assistance, not infrastructure the user must administer.

## Automation and AI Opportunities

### Safe for the first product

| Opportunity                                         | Default           | User control                  |
| --------------------------------------------------- | ----------------- | ----------------------------- |
| Discover candidate birthdays from selected contacts | Preview only      | Select and confirm            |
| Detect likely duplicate people or moments           | Suggest only      | Compare, merge, keep separate |
| Rank Today actions by date and explicit urgency     | On                | Dismiss or snooze             |
| Prepare a local draft before a moment               | Off initially     | Opt in per moment/person      |
| Suggest missing context during composition          | On, non-blocking  | Skip permanently or answer    |
| Detect duplicate or overly similar messages         | On                | Edit or explicitly continue   |
| Reconcile app-owned reminders after edits           | On                | Category settings and disable |
| Suggest follow-up after a user-confirmed send       | Off initially     | Choose date or dismiss        |
| Warn when backup is stale and data is valuable      | On, low frequency | Back up or dismiss            |

### Appropriate only after trust and reliability are proven

- Optional encrypted multi-device continuity.
- Proactive AI draft preparation for selected people.
- Verified provider email delivery with OAuth and clear delivery semantics.
- Shared family/couple occasion planning.
- Limited scheduled delivery where platform policy, revocation, observability, and duplicate prevention are proven.

### Do not build

- Global unattended sending.
- Inbox or social-media scraping.
- Automatic relationship classification presented as fact.
- Numerical relationship health or neglected-person rankings.
- Bulk personal-message approval or sending in the consumer product.
- Provider endpoint, SMTP credential, or infrastructure configuration for ordinary users.
- AI that independently chooses recipient, content, time, and channel.

### AI quality contract

- AI is never required for the core product.
- The user sees and can remove optional context before generation.
- Private notes are excluded by default and require explicit item-level eligibility.
- Suggestions state their target language and tone in human terms.
- AI failure never discards user text.
- The app does not claim emotional understanding, relationship health, or delivery knowledge it cannot observe.
- User edits have priority over model output and are not silently overwritten.
- Sensitive content, provider responses, and message bodies stay out of logs and diagnostics.

## Reporting Opportunities

The current analytics dashboard and CSV report should not ship in the MVP. Metrics such as relationship health, neglect, personalization coverage, and delivery success are either judgmental, incomplete, or based on app activity rather than relationship outcomes.

An optional later “Reflection” can show only defensible facts:

- Moments prepared for or missed.
- Drafts the user reviewed.
- Check-ins the user marked complete.
- Upcoming moments without a plan.
- Backup age.

It should avoid streaks, grades, leaderboards, relationship scores, and language designed to increase send volume. CSV export should be added only after real user demand and a clear privacy use case.

For users who explicitly opt in, business analytics must be aggregate and privacy-minimized. Do not collect contact names, dates, notes, message bodies, or relationship graphs. Useful measures include activation completion, time to first value, feature opt-in, failure categories, retention cohorts, and anonymized funnel exits.

## Collaboration Opportunities

Collaboration is not MVP scope. If the single-user loop proves value, a later shared circle could let invited family members or partners:

- share selected birthdays and anniversaries;
- coordinate who is planning a gift or gathering;
- assign preparation tasks;
- maintain a shared factual note with visible ownership.

Collaboration must require explicit invitations, record-level sharing, revocation, and clear ownership. Private notes, writing samples, message drafts, sent-message history, contact routes, and AI context must never become shared by default. There should be no public feed, social graph, or silent contact matching.

## Guidance, Validation, and Error Expectations

### Guidance

- Explain benefits before permissions, not during app launch.
- Use progressive disclosure and one recommended next action.
- Empty states teach the next meaningful step.
- Show examples for unfamiliar inputs such as custom dates or “what to avoid.”
- Keep technical diagnostics behind Help and generate a redacted support report only on demand.
- Never use setup completion as the primary motivation; show the user outcome unlocked by a step.

### Validation

- Validate dates, names, contact routes, message length, recurrence, duplicate risk, and backup passphrase inline.
- Preserve all typed text on validation or network failure.
- Show downstream impact before changing a date, deleting a person, replacing data, or disabling a route.
- Recheck stale data immediately before approval, restore, delete, or handoff.
- Use one confirmation for one high-risk user intent; avoid chained tokens and repeated dialogs.

### Errors

Every error state answers:

1. What did not happen?
2. Was my data or draft preserved?
3. What can I do now?

Examples:

- “We couldn't create an online suggestion. Your text is safe. Use a local template or try again.”
- “Notifications are off, so this reminder will appear only in Today. Open device settings.”
- “This backup could not be verified. Nothing was replaced. Choose another file.”
- “WhatsApp did not open. Copy the approved message or choose another app.”

Error codes, provider categories, endpoint state, operation scopes, and storage phases stay out of the default experience.

## Performance and Reliability Expectations

- A warm launch shows cached Today content within one second; a typical cold launch becomes actionable within two seconds.
- Saving a local edit gives visible confirmation within 300 ms and durably completes without blocking navigation.
- Local search/filter feedback appears within 100 ms for typical datasets.
- Local templates render immediately and work offline.
- AI shows progress promptly, supports cancellation, and returns a useful result within a product-defined timeout; timeout falls back without losing text.
- Import and restore show progressive status, remain cancellable where safe, and do not freeze scrolling or typing.
- Core lists remain responsive across the usage envelope validated in discovery. Until production evidence establishes that envelope, large synthetic datasets are stress tests—not a claim that ordinary users need or can use 10,000 imported people.
- Background interruption, process death, time-zone change, and repeated notification taps do not duplicate data or actions.
- No message is marked verified delivered when the app only knows that a handoff opened or the user marked it sent.
- Destructive clear and restore are all-or-nothing from the user's perspective.

## Accessibility, Localization, and Inclusive UX

- English, Hindi, and Hinglish need natural human review, not literal key coverage.
- All core flows work with screen readers, large text, reduced motion, and compact screens.
- Touch targets meet platform guidance and controls expose label, role, state, and error association.
- Date entry avoids locale ambiguity and previews the interpreted date.
- Status never relies on color alone.
- Notification content is privacy-minimized on lock screens.
- AI language is explicit and correctable per person and message.
- No copy shames users for relationship frequency, missed moments, sparse data, or declined permissions.

## Explicit Product Decisions

### Keep as product constraints, not inherited UI

- Local-first usefulness.
- Review before any external communication.
- Manual SMS, WhatsApp, mail, copy, and share handoff.
- Duplicate prevention and stale-data rechecks.
- User-initiated, purpose-specific permissions.
- Private-data redaction and secure local storage.
- Preview-before-restore and atomic recovery.
- Optional biometric lock.
- Accessible, localized, interruption-safe behavior.

### Merge

- AI generation, Manual Composer, Template Library, regeneration, and Wish Preview into Compose and Review message.
- Memory Vault, Chat History, Gift Advisor, notes, and relationship timeline into the Person timeline, with contextual gift planning.
- Events, event checklist, reminder planning, and date conflicts into Moments and Moment preparation.
- Setup Check, permission readiness, and operational issues into contextual Fix this actions; retain detailed diagnostics only under Help.
- Style Coach, recipient tone, language, emoji, and length controls into Writing preferences, with person-level overrides.
- Privacy, permissions, biometric lock, export, backup, restore, and delete into Privacy & Data.

### Redesign

- Onboarding around first saved moment, not nine setup steps.
- Home as Today, not a dashboard of scores and readiness.
- Contact profile as Person overview plus timeline and progressive preferences.
- Message lifecycle around Draft, Ready to act, Needs attention, and Marked sent; hide internal provider states.
- Check-ins as explicit opt-in reminders rather than a global relationship-health system.
- Import as selected people plus date confirmation, not full-database ingestion by default.
- Notifications around user-created value and category controls.
- Backup around understandable recovery choices and timely prompts.

### Remove from the initial product

- Numeric relationship health, “neglected contacts,” and health buckets.
- Numeric personalization coverage as a goal.
- Full global automation, Smart approve, VIP approve, and sending blackouts.
- Bulk approve/reject/retry/send workflows.
- Analytics dashboard, relationship CSV, and shareable health report.
- Provider endpoint and session setup in consumer settings.
- Google sync claims until a real, end-to-end supported journey exists.
- Provider email until a turnkey authenticated connection exists.
- Revival as an event type.
- Activity History, Setup Check, Memory Vault, Gift Advisor, Chat History, Template Library, and Style Coach as primary standalone destinations.
- Widget and multiple launcher shortcuts from MVP scope.
- Gift budgeting and AI gift suggestions until the core loop proves retention.

## Success Metrics

### User value

- Median time from install to first saved person and moment.
- Percentage of new users who create one reminder or review one draft in the first session.
- Percentage of upcoming moments with a user-chosen action completed before the date.
- Draft usefulness: accepted, edited, abandoned, and local-template fallback rates without storing message content in analytics.
- Reminder usefulness: acted on, snoozed, dismissed, and notification-disable rates.
- Successful backup and restore test rates.

### Trust and quality

- Zero unintended sends attributable to the app.
- Zero messages marked delivered without verified evidence.
- Duplicate-prevention escape and false-positive rates.
- Permission acceptance after contextual explanation, without optimizing through coercion.
- Crash-free and data-recovery success rates.
- Accessibility completion rates for the same core journeys.
- Privacy/security incidents, with zero as the only acceptable target.

### Business

- Week-4 and month-3 retention among users who saved at least one recurring moment.
- Conversion to a paid value hypothesis such as high-quality online suggestions or encrypted continuity.
- Support contacts per active user, especially provider/setup and recovery issues.
- Cost per useful AI suggestion and fallback rate.
- Churn reasons tied to trust, setup effort, generic output, or insufficient recurring value.

Do not optimize raw contact count, message volume, notification volume, or time in app. Those can increase while user relationships and trust get worse.

## Product Reset Sequence

### Phase 0 — Stop and learn

- Interview target users about actual reminder, writing, privacy, and follow-through behavior.
- Compare the concept against a calendar plus a general AI assistant.
- Test whether the proposed Indian professional/family wedge is meaningfully narrower and more reachable.
- Establish willingness to return and test willingness to pay before production implementation.

### Phase 1 — Figma experience proof

- Test a clickable prototype of first run, Today, Moment preparation, Compose, Review, manual handoff, context disclosure, and data exit.
- Include permission denial, provider failure, stale-link, empty, and recovery states.
- A disposable first iteration may use English to isolate structural issues, but the production gate requires representative Hindi and Hinglish prototype evidence and human language review.
- Do not carry visual or interaction assumptions from the JSON console into the prototype.

### Phase 2 — Narrow production vertical slice

- Today, Moments, and People navigation with contextual Compose and Review.
- Manual person/moment creation and selective device-contact import.
- Birthday, anniversary, and custom reminders.
- Local templates, one composer, duplicate warning, and manual handoff.
- Person timeline with optional assistance-eligible context.
- Contextual notification permission.
- Privacy & Data with encrypted export/restore, delete-all, app lock, support, and policy paths.
- English, Hindi, and Hinglish behavior reviewed by humans before beta distribution.
- Add operated authenticated assistance only as a later substage after the complete local loop and its value hypothesis pass their gates.

### Phase 3 — Habit and trust beta

- Experiment with recipient writing preferences, assisted-writing entry, dynamic preparation, opt-in check-ins, and follow-up suggestions.
- Validate reminder frequency, retained context, interruption recovery, and the real supported data envelope.
- Keep or remove each experiment using observed action, dismissal, trust, and support outcomes.

### Phase 4 — Monetization proof

- Test a real paid offer for privacy-preserving assisted writing or encrypted continuity.
- Keep reminders, templates, export, deletion, privacy, and accessibility in the free core.
- Build billing only after conversion and sustainable provider/support economics are credible.

### Phase 5 — Selective expansion

- Consider encrypted continuity, calendar import/export, a minimal widget, factual reflection, or explicit shared circles only where measured demand justifies the risk.
- Never share private notes, message content, or writing profiles by default.
- Proceed with collaboration only if single-user retention, consent design, and privacy trust are strong.

## Final Recommendation

Do not design Figma screens for the existing feature index one by one. That would make a polished version of the current fragmentation.

Start Figma and product requirements from the three core destinations and the eight ideal journeys in this document. Treat existing application services as implementation candidates that must adapt to the approved journey—not as constraints on information architecture, terminology, interaction count, or scope.

The product deserves a rebuild only if discovery validates a recurring need beyond reminders and generic AI. If validated, the winning version will be smaller, quieter, more honest, and more contextual than the current specification: fewer destinations, no relationship scoring, no provider administration, no autonomous sending, one writing flow, and a relentless focus on helping the user act thoughtfully at the right moment.
