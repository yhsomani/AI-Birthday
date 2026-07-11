# RelateAI Feature Roadmap Analysis

Last updated: 2026-07-10

> **Superseded on 2026-07-11.** This prior roadmap assumed the broad feature inventory was the correct starting point. It is retained as historical input only. Use `../SSOT.md` and `product-reset/product-vision-and-roadmap.md` for current product direction and evidence gates.

This document uses `docs/feature-fssot.md` as the feature scope baseline and identifies:

- Features that are not useful enough for ordinary end users to be prominent.
- Features that should be removed, postponed, hidden, or simplified.
- Useful features that should be added to make the product more valuable.

The goal is product prioritization, not implementation planning.

## Evaluation Criteria

A feature is useful for end users when it does at least one of these clearly:

- Helps the user remember an important relationship moment.
- Helps the user write or send a better message with less effort.
- Helps the user add personal context that improves future messages.
- Helps the user avoid mistakes, missed events, duplicate sends, or privacy problems.
- Builds trust by giving the user clear control over personal communication.

A feature is weak or not useful when it mainly:

- Exposes setup or diagnostic complexity instead of solving a user problem.
- Exists for edge cases that most users will rarely touch.
- Adds policy, privacy, or trust risk without a strong everyday benefit.
- Competes with the core birthday/event/message workflow.
- Requires high user effort before giving visible value.

## Recommended Product Shape

RelateAI should focus the main experience around four user outcomes:

1. Remember important dates.
2. Prepare thoughtful messages.
3. Keep useful relationship context.
4. Safely review and send at the right time.

Recommended primary navigation:

1. Home
2. Events
3. Messages
4. Contacts

Recommended secondary destinations:

- Settings
- Backup and Restore
- Style Coach
- Memory Vault
- Gift Advisor
- Analytics
- Activity History
- Setup Check

Analytics should not be a primary bottom-navigation item for the average user. It is useful, but it is not more important than acting on upcoming events or reviewing messages.

## Features That Are Core and Useful

These features directly support the product promise and should remain first-class.

| Feature | Why it is useful | Product priority |
| --- | --- | --- |
| Home Dashboard and Relationship Planner | Tells users what to do next and reduces decision fatigue. | Highest |
| Events and Reminders | Core reason users install the app: do not miss birthdays, anniversaries, and important relationship moments. | Highest |
| AI Message Generation | Creates the main value: thoughtful personalized wishes with less effort. | Highest |
| Wish Preview and Editing | Gives users final control before messages are sent. | Highest |
| Messages Inbox and Approval Lifecycle | Keeps pending, scheduled, failed, and sent messages understandable. | Highest |
| Contacts and Contact Detail | Provides the people, delivery channels, and personalization data needed for every other feature. | Highest |
| Memory Vault | Lets users add the personal details that make AI messages feel specific. | High |
| Notifications and Event Alerts | Prevents missed reminders and approval windows. | High |
| Privacy, Permissions, and Security Controls | Required for trust because the app handles private relationship data and outgoing messages. | High |
| Backup and Restore | Important because relationship data is private, local, and hard to recreate. | High |
| Localization and Accessibility | Required for broad usability and release quality. | High |

## Features That Are Not Useful Enough as Prominent End-User Features

These features may still be useful, but they should not compete with the core workflow.

| Feature | Usefulness issue | Recommendation | Better product treatment |
| --- | --- | --- | --- |
| AI Doctor as a standalone major feature | Most users do not want to diagnose AI/provider systems; they want the app to tell them the next fix. | Keep, but hide under Settings and contextual error states. | Rename or present as "Setup Check" or "Fix Issues" instead of a separate product pillar. |
| Activity History | Useful for troubleshooting, but not a daily relationship workflow. | Keep as support/audit trail only. | Surface relevant activity inside contact/message details; keep full history in Settings. |
| Analytics as primary navigation | Metrics are interesting, but users get more value from reminders, drafts, and actions. | Move out of bottom navigation. | Show key insights on Home; keep full Analytics under More/Settings. |
| CSV report export | Niche power-user need; most consumer users will not export relationship reports. | Keep only if cheap; otherwise postpone. | Replace prominence with simple shareable weekly/monthly summary later. |
| Launcher shortcuts | Low unique value because Home and notifications already route users to tasks. | Postpone or keep very minimal. | Use only for "Review messages" and "Add event" if shortcuts are retained. |
| Home widget | Potentially useful but not core; many users do not use widgets. | Nice-to-have, not MVP-critical. | Keep simple: today's events and pending approvals only. |
| Style Coach profile history snapshots | Users care that messages sound like them, not a detailed history of extracted style profiles. | Hide or simplify. | Show confidence and "Improve my style" action; keep history out of the main UI. |
| Channel verification details | Important for reliability, but too operational for most users. | Hide behind setup warnings. | Say "Test SMS before auto-send" rather than showing verification internals. |
| Dispatch recovery records | Useful for support, not ordinary users. | Do not expose as a normal feature. | Convert into simple failed-message recovery actions. |
| Advanced setup diagnostics | Can overwhelm users and make the app feel fragile. | Collapse into one recommended fix. | Show detailed checks only after "Show details." |
| Bulk message actions | Risky for personal communication and low frequency early on. | Postpone until single-message review is excellent. | Add later for trusted users with confirmation and clear eligibility. |
| Full automation mode | High trust risk because personal messages can feel wrong if sent unattended. | Do not make it a primary selling point. | Default to review-first; make full automation advanced and contact-specific. |
| WhatsApp Accessibility automation | High policy, trust, and reliability risk compared with user benefit. | Avoid as core MVP feature. | Prefer manual "Open in WhatsApp with approved text" or review-first handoff. |
| Email/Gmail SMTP sending | Useful for some professional contacts, but setup friction is high. | Secondary channel, not primary setup. | Keep SMS/manual share first; offer email only for users who need it. |
| Gift budget accounting | Useful for gift-heavy users but not core relationship reminder behavior. | Keep inside Gift Advisor, not Contact Detail primary UI. | Focus Gift Advisor on gift ideas and avoiding repeats; budget can be optional. |
| Broad event types such as graduation, holidays, revivals, and follow-ups | Some are useful, but too many categories can dilute the birthday/anniversary use case. | Keep but de-emphasize. | Default to birthdays, anniversaries, and custom reminders; reveal more types as advanced. |
| Biometric lock as an upfront setup item | Valuable for privacy, but not necessary before the user sees product value. | Keep in Settings; do not force early. | Offer after the user adds private notes or credentials. |

## Features to Remove or Postpone for a Focused MVP

These should not block the first strong end-user version.

| Feature | Decision | Reason |
| --- | --- | --- |
| Fully unattended WhatsApp sending | Postpone or replace with manual handoff | Too much platform-policy and user-trust risk for a core release. |
| Full automation as a global default | Postpone | Review-first messaging is safer and easier to trust. |
| CSV analytics export | Postpone | Low-frequency power-user feature. |
| Detailed diagnostic snapshots and recovery records in UI | Hide | Users need fixes, not diagnostic artifacts. |
| Style profile history UI | Hide | Low end-user value compared with showing current style quality. |
| Bulk approve/retry/reject | Postpone | Personal communication deserves individual review until the product earns trust. |
| Multiple advanced event categories on first-run | Hide behind custom event flow | Too many choices slow the main birthday/anniversary workflow. |
| Launcher shortcuts beyond one review shortcut | Postpone | Low impact relative to Home, notifications, and widget. |

## Useful Features to Add

These additions would make the product more valuable to end users than many advanced diagnostics or automation controls.

### 1. Manual Message Composer

Purpose: Let users create a thoughtful message even when there is no upcoming event or AI is disabled.

Why it helps: Users often want to reconnect, apologize, congratulate, thank, or check in outside birthdays and anniversaries.

Ideal workflow:

1. User opens a contact and taps "Write message."
2. User chooses a reason: birthday, check-in, thanks, congratulations, apology, follow-up, or custom.
3. App presents local templates matched to contact tone and explains what non-private context is included.
4. App shows whether AI is ready, unavailable, disabled, or falling back to a local review-first draft.
5. User edits the message, creates a local template draft, or requests AI variants.
6. Draft always enters review before scheduling, handoff, or sending.

Acceptance expectations:

- Local templates remain usable when AI is disabled or unavailable.
- Private notes are excluded and the exclusion is visible.
- Too-short edited messages are blocked with actionable guidance.
- AI readiness or fallback behavior is visible before the user acts.

Priority: Highest.

### 2. Relationship Check-In Reminders

Purpose: Remind users to reconnect with people after a chosen cadence.

Why it helps: Relationship maintenance is broader than occasion messages.

Ideal workflow:

1. User sets a cadence per contact or group, such as every 30, 60, or 90 days.
2. App reminds the user when the relationship has been quiet.
3. User can write a check-in, snooze, or mark as contacted elsewhere.

Priority: Highest.

### 3. Event Preparation Checklist

Purpose: Turn upcoming events into a small action plan.

Why it helps: Users often need more than a message: confirm date, add memory, prepare gift, draft wish, review send time.

Ideal workflow:

1. User opens an upcoming event.
2. App shows checklist items: confirm date, improve context, write wish, decide gift, choose channel, schedule reminder.
3. User completes only the relevant items.

Acceptance expectations:

- Gift preparation appears only for event types where it is useful.
- The checklist explains the next recommended action and whether each step is done or needs action.
- Existing memories, drafts, reminder plans, and ready channels can satisfy matching steps without duplicate work.
- Older saved checklist item ids remain compatible with the canonical preparation steps.

Priority: High.

### 4. Guided Contact Enrichment

Purpose: Help users add the right context without staring at an empty profile.

Why it helps: AI quality depends on context, but users need prompts to know what to add.

Ideal workflow:

1. App identifies missing relationship details.
2. User answers lightweight prompts such as "How do you know them?", "What should the message mention?", "What should it avoid?", and "What language feels right?"
3. The profile quality improves immediately.

Acceptance expectations:

- Sparse contacts show all four core enrichment prompts.
- The profile explains completed and missing personalization signals.
- Saved answers become reusable non-private context unless the user chooses a private note path.
- Activity and diagnostics mention that context was saved without logging the user's answer text.

Priority: High.

### 5. Privacy-Aware Manual Send Handoff

Purpose: Let users approve text and then open their preferred app with the message ready to send manually.

Why it helps: It avoids the trust and platform risk of unattended sending while still saving time.

Ideal workflow:

1. User approves a message.
2. App offers "Open in SMS," "Open in WhatsApp," or "Copy message."
3. User sends from the destination app manually.
4. User can mark as sent in RelateAI.

Acceptance expectations:

- Opening a destination app or share sheet never marks the message sent by itself.
- The app always offers a copy/share fallback.
- RelateAI records sent status only after the user explicitly confirms the message was sent.
- Manual handoff rechecks approval window, route readiness, and message body policy before recording sent status.

Priority: High.

### 6. Calendar View and Calendar Export

Purpose: Give users a familiar monthly view and optional device-calendar visibility for relationship events.

Why it helps: Events are easier to trust when users can see them in a calendar format and optionally mirror them to their normal calendar.

Ideal workflow:

1. User opens Events and switches to Month view.
2. User sees birthdays, anniversaries, and custom events by date.
3. User exports selected reminders to device calendar if desired.

Priority: High.

### 7. Smart Event Import from Calendar and Files

Purpose: Add events from device calendar, CSV, or vCard import.

Why it helps: Many important dates are not stored in contacts.

Ideal workflow:

1. User chooses import source.
2. App previews candidate events.
3. User selects, edits, and confirms imports.
4. Conflicts go to event review.

Priority: Medium.

### 8. Message Template Library

Purpose: Provide useful non-AI templates for common relationship moments.

Why it helps: Users can still get value offline, in local mode, or when AI is disabled.

Ideal workflow:

1. User chooses an occasion and tone.
2. App shows editable templates.
3. User personalizes, saves, schedules, or sends manually.

Acceptance expectations:

- The library is reachable outside the Manual Composer.
- Users can choose contact, occasion, tone, and template before editing.
- Missing exact tone matches fall back to available templates with clear explanation.
- Template drafts are created as review-first messages and never sent directly.

Priority: Medium.

### 9. Relationship Timeline

Purpose: Combine key memories, gifts, events, and sent messages into one contact timeline.

Why it helps: Users should not jump across Memory Vault, Gift Advisor, Chat History, and Events to understand a relationship.

Ideal workflow:

1. User opens a contact.
2. Timeline shows recent notes, events, gifts, messages, and follow-ups.
3. User filters by type or adds a new timeline item.

Priority: Medium.

### 10. Follow-Up After Sent Message

Purpose: Help users continue the relationship after a wish is sent.

Why it helps: The real goal is connection, not just sending a greeting.

Ideal workflow:

1. After a message is sent, app offers optional follow-up reminder.
2. User chooses "tomorrow," "next week," or "no follow-up."
3. App reminds them to ask how the event went or continue the conversation.

Priority: Medium.

### 11. Recipient-Specific Tone Controls

Purpose: Make message generation more predictable per contact.

Why it helps: Users need different tones for parents, managers, close friends, and acquaintances.

Ideal workflow:

1. User sets tone preferences on contact profile: warm, respectful, playful, concise, no emoji, Hinglish, formal English, etc.
2. Wish Preview shows how the preference affected the draft.
3. User can adjust tone without retraining global style.

Success criteria:

- Contact profile lets users add, remove, inherit, or override supported tone preferences.
- Wish Preview shows effective tones, language target, preference source, draft quality, and a plain-language explanation of tone impact.
- Wish Preview provides a direct adjustment path to the contact profile.
- Tone changes affect future drafts for that contact and require unsent affected drafts to be reviewed again.
- Template and fallback drafts still show the intended tone target and remain review-first.
- Global style training is never required to change a single recipient's tone.

Priority: Medium.

### 12. Safe Duplicate-Send Guardrail

Purpose: Make duplicate prevention visible before approval and send.

Why it helps: Accidentally sending the same or similar greeting twice is one of the highest-trust failures.

Ideal workflow:

1. User reviews a draft.
2. App warns if a similar message was already sent or scheduled for the same event.
3. User can cancel, edit, regenerate, or explicitly continue.

Priority: Medium.

### 13. User-Friendly Setup Wizard

Purpose: Replace diagnostic-heavy setup with a guided setup path.

Why it helps: Users do not want to understand every readiness check; they want the next step.

Ideal workflow:

1. User opens setup.
2. App asks what they want: reminders only, AI drafts, manual sends, or automation.
3. App shows only the required steps for that choice, including release-ready AI provider readiness for AI drafts and email provider readiness only when provider-backed email has been chosen or configured.

Priority: Medium.

### 14. Data Usage and AI Context Preview

Purpose: Show users what context will be used before AI generation.

Why it helps: Builds trust and helps users improve personalization.

Ideal workflow:

1. User opens Wish Preview or generation screen.
2. App shows "AI will use: relationship, event, style, selected memories, prior wishes."
3. User can exclude optional context before regenerating.

Priority: Medium.

### 15. Contact Groups and Circles

Purpose: Let users organize relationship priorities without overloading individual profiles.

Why it helps: Users often think in groups: family, close friends, work, clients, classmates.

Ideal workflow:

1. User creates or confirms groups.
2. Group defaults control reminders, tone, and automation review level.
3. Individual contacts can override group settings.

Priority: Later.

## Additions to Avoid for Now

These may sound attractive but would likely distract from the core product.

| Addition | Why to avoid now |
| --- | --- |
| Fully automated AI conversation management | Too invasive and trust-risky for personal relationships. |
| Reading WhatsApp or SMS inbox content automatically | High privacy and policy risk. |
| Social media scraping | High privacy risk and unclear user control. |
| Gift shopping affiliate marketplace | Turns a relationship assistant into commerce too early. |
| Complex CRM pipelines | Not aligned with personal relationship reminders. |
| Public social feed or sharing | Personal relationship data should stay private by default. |
| Gamified relationship scores | Can feel manipulative or judgmental. |

## Recommended Roadmap

### MVP Focus

Build the smallest strong version around:

- Contact import and manual contacts.
- Birthday, anniversary, and custom event reminders.
- AI or template message drafting.
- Wish preview, edit, and review-first approval.
- Manual send handoff or safe SMS send.
- Memory Vault prompts for better personalization.
- Home next-best-action dashboard.
- Backup, privacy controls, and accessible/localized UI.

### Next Release

Add:

- Relationship check-in reminders.
- Event preparation checklist.
- Guided contact enrichment.
- Calendar month view.
- Follow-up after sent message.
- Recipient-specific tone controls.

### Later

Consider only after trust and core workflows are strong:

- Full automation for selected low-risk contacts.
- Advanced analytics.
- CSV export.
- Home widget.
- Bulk message actions.
- Email sending.
- WhatsApp automation, only if policy and user trust requirements are fully satisfied.

## Final Recommendation

The most useful version of RelateAI is not the one with the most automation. It is the one that helps users remember important people, write better messages, and stay in control.

The product should reduce prominence of diagnostics, reports, bulk actions, full automation, and risky channel automation. It should add more user-centered workflows: manual composing, check-in reminders, event preparation, guided enrichment, calendar views, and safe manual send handoff.
