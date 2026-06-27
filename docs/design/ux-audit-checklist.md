# UX Audit Checklist

Last reviewed: 2026-06-27

This checklist tracks the RelateAI UI redesign without changing feature behavior. A screen is production-ready only when its purpose, owner feature, navigation entry, empty/loading/error states, accessibility, and tests are verified.

## Audit Principles

- Each feature has one primary home. Other screens may link to it, summarize it, or expose a contextual shortcut, but must not duplicate full ownership.
- Primary navigation is for repeated daily workflows. Secondary routes are allowed for setup, detail, audit, and recovery flows.
- Every screen must answer: what is the user trying to do, what state is the app in, what is the next safe action, and how do they recover?
- Visual redesign must preserve ViewModel behavior, route contracts, persistence contracts, dispatch eligibility, and permission gates.
- Screens must be readable at large font scale and narrow mobile widths before a task is closed.

## Screen Ownership Decisions

| Screen | Current route | Decision | Primary purpose | Merge or cleanup rule |
| --- | --- | --- | --- | --- |
| Onboarding | `onboarding` | Keep as entry/setup bridge | Explain product value and route users to setup. | Do not duplicate AI Doctor checks; show setup progress summary and link to AI Doctor. |
| Home | `home` | Keep as operational dashboard | Show next best action, setup progress, upcoming work, and critical stats. | Home owns summaries only. Full queues stay in Messages, full reports stay in Analytics, full setup stays in AI Doctor. |
| Contacts | `contacts` | Keep in bottom navigation | Search, sync, filter, sort, and open contact records. | Contact editing and relationship context stay in Contact Detail. |
| Contact Detail | `contacts/{contactId}` | Keep as contextual workspace | Relationship profile, preferences, events, memory/gift/style links, and history context. | Memory Vault, Gift Advisor, Style Coach, and Chat History should be launched contextually from here, not duplicated as primary nav. |
| Events | `events` | Keep in bottom navigation | Occasion management, manual events, conflicts, and verification. | Message review stays in Messages/Wish Preview; Events should not become a send queue. |
| Messages | `messages` | Keep in bottom navigation | Review, scheduled, blocked, failed, sent, and bulk recovery queues. | Messages owns queue state. Wish Preview owns one draft review/edit flow. |
| Wish Preview | `wish/{contactId}/{messageRef}` | Keep as detail task flow | Review/edit/regenerate/test/approve/reject one draft. | Do not expose full queue management here beyond next-review affordance. |
| Analytics | `analytics` | Keep in bottom navigation for now | Reporting, health distribution, trend summaries, CSV export. | Must not duplicate Home next actions or Activity History audit rows. If usage is low, consider moving to secondary More/Insights later. |
| Activity History | `activity-history` | Keep as secondary audit route | Filterable operational audit trail. | Analytics can link to it; do not duplicate raw audit tables in Analytics. |
| AI Doctor | `automation-setup` | Keep as setup/recovery center | Permissions, channels, AI, exact alarms, WorkManager, recovery checks, dry run. | Settings may link to it, but channel setup/diagnostics live here. |
| Settings | `settings` | Keep as secondary/global route | Credentials, global preferences, sign-out, links to setup/data tools. | Avoid duplicating AI Doctor diagnostics or Backup workflows. |
| Backup/Restore | `backup-restore` | Keep as secondary data route | Export, preview, restore, and backup status. | Settings/Home may link; backup logic stays here. |
| Memory Vault | `memory-vault/{contactId}` | Keep contextual | Contact memory notes. | No primary navigation until there is a cross-contact memory workflow. |
| Gift Advisor | `gift-advisor/{contactId}` | Keep contextual | Gift history and suggestions for a contact. | No primary navigation until there is a cross-contact gift planner. |
| Style Coach | `style-coach/{contactId}` | Keep contextual | Writing style analysis/profile for a contact. | Should feed personalization context; avoid duplicating Wish Preview editing. |
| Chat History | `chat-history/{contactId}` | Keep contextual or merge candidate | Contact-specific conversation/message history. | Candidate to merge into Contact Detail history tab if future UI adds tabs. Do not remove until behavior and data ownership are validated. |
| Splash/Auth/Biometric gate | app shell/auth routes | Keep | Startup, identity, guest mode, and app lock. | Must remain minimal, accessible, and fast. |

## Project Checklist

Information architecture:

- Primary bottom navigation contains only Home, Contacts, Events, Messages, and Analytics.
- Settings, AI Doctor, Backup/Restore, Activity History, and contextual tools are reachable in two taps or fewer from relevant primary screens.
- Each feature has one owner route listed in Screen Ownership Decisions.
- Duplicate controls are links or shortcuts, not independent implementations.
- Deep links and shortcuts route to current owner screens.

Layout and hierarchy:

- Each screen has one clear heading, one primary action area, and predictable secondary actions.
- Dense operational screens use lists, filters, tabs, and compact cards instead of marketing-style sections.
- Summary dashboards show only the highest-priority metrics and actions.
- Empty states explain what is missing and offer the next safe action.
- Loading states use progress indicators or skeletons where content shape is known.
- Error states show recovery actions and do not expose secrets or raw provider payloads.

Components:

- Shared UI uses `core/ui` tokens before introducing screen-local dimensions.
- Cards use radius 8 dp or less unless a component is a pill/chip.
- Icon buttons use recognizable icons with content descriptions when the icon is the only label.
- Buttons are reserved for commands; tabs, filters, chips, switches, checkboxes, sliders, and menus represent choices.
- No cards inside cards.
- No decorative gradient/orb backgrounds.

Accessibility:

- Touch targets are at least 48 dp where practical.
- Text has sufficient contrast against its container.
- User-facing icons have labels or content descriptions.
- State changes that need attention use polite live regions where appropriate.
- Large font scale review checks title truncation, button text wrapping, filter chips, cards, and bottom navigation.
- Hindi and English strings are checked for clipping and stale copy.

Performance:

- Large lists use lazy containers.
- Expensive work stays in ViewModels/use cases, not Composables.
- Empty/loading/error states render progressively.
- Screens do not block first paint on optional diagnostics or exports.

Validation:

- ViewModel tests cover behavior-preserving UI state.
- Screen interaction tests cover critical commands and disabled states.
- Screenshot or device validation covers primary workflows at small width and large font scale.
- `git diff --check`, focused tests, and relevant compile tasks pass before closing a UI slice.

## Open Redesign Work

- Define a formal screenshot testing strategy and store expected viewport/font-scale coverage.
- Add light/dynamic theme decision after dark-theme redesign is stable.
- Convert screen-local spacing, colors, and shapes to shared tokens incrementally.
- Split oversized screens after behavior is covered by tests: Messages, Contact Detail, Events, Settings, Gift Advisor, Wish Preview, Home, and AI Doctor.
- Re-evaluate Analytics as a primary bottom-nav item after first screenshot/usage review.
