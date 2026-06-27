# UX Audit Checklist

Last reviewed: 2026-06-28

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

- Completed slice 2026-06-27: AI Doctor keeps its current route and feature ownership, while the screen now uses shared spacing/radius/size/alpha tokens, keeps check actions attached to their owning row copy, and avoids nested cards in the recommended-fix surface. Screenshot and large-font validation remain pending.
- Completed slice 2026-06-27: Onboarding remains the setup bridge and does not duplicate AI Doctor diagnostics, while the hero, setup checklist rows, step badges, and primary/secondary actions now use shared spacing/radius/size tokens. The continue action still completes onboarding through the ViewModel, and the setup checklist action still opens AI Doctor/setup.
- Completed slice 2026-06-27: Auth remains the startup identity gate, while Google sign-in, debug-only guest bypass, signed-in routing, loading/error/legal surfaces, and debug visibility now have focused coverage. The screen uses shared spacing/radius/size/alpha tokens without changing launcher, ViewModel, or `BuildConfig.DEBUG` behavior.
- Completed slice 2026-06-27: Splash remains the startup routing gate, while brand, subtitle, fade, and progress indicator presentation now use shared spacing/size/alpha tokens with focused content coverage. Destination resolution timing and navigation callbacks are unchanged.
- Completed slice 2026-06-27: Splash compact-phone Roborazzi baselines now cover default and large-font startup rendering. The screenshot test freezes the pure `SplashContent` at a deterministic visible progress frame and does not change routing, animation timing, or destination resolution behavior.
- Completed slice 2026-06-27: Home keeps existing next-action, setup-progress, quick-action, planner, and upcoming-event behavior, while the screen now uses shared spacing/radius/size/alpha tokens, extracts stats/quick actions into presentation helpers, and reserves loading-panel space to avoid layout jump. Future IA cleanup must validate any quick-action removal before changing navigation affordances.
- Completed slice 2026-06-28: Home compact-phone Roborazzi baselines now cover the populated operational dashboard, large-font dashboard top, a lower large-font dashboard/action viewport, and the loading state. The screenshots render deterministic `HomeUiState` fixtures through `HomeContent` only, preserving ViewModel loading, navigation, and data behavior.
- Completed slice 2026-06-27: Contact List remains the primary Contacts owner, while search, filter chips, sort chips, refresh/sync-error surfaces, loading skeletons, empty state, row avatars, health dots, and quality labels now use shared spacing/radius/size/alpha/fraction tokens. Search, filter, sort, refresh, dismiss, and row navigation callbacks are unchanged.
- Completed slice 2026-06-27: Wish Preview keeps the existing single-draft review/editor behavior, while the screen now uses shared spacing/radius/size/alpha/fraction tokens across variants, editor, feedback chips, summary/why panels, metadata rows, and review actions. Screenshot and large-font validation remain pending.
- Completed slice 2026-06-27: Backup/Restore remains a separate secondary data-safety route, while warning, passphrase, password-strength, export/import action, preview-confirmation, success, and error surfaces now use shared spacing/radius/size/alpha/elevation/fraction tokens. Export/import behavior and replace-restore confirmation are unchanged.
- Completed slice 2026-06-27: Contact Detail remains the contextual relationship workspace, while profile, essentials, personalization, automation, history, and preference-dialog surfaces now use shared spacing/radius/size tokens. Preference options wrap for longer labels without changing saved preference behavior.
- Completed slice 2026-06-27: Events remains the occasion workspace, while filters, manual-event choices, event cards, metadata chips, conflict actions, and snackbar surfaces now use shared spacing/radius/size/alpha tokens. Manual save validation, duplicate warnings, filtering, refresh, and conflict-resolution callbacks are unchanged.
- Completed slice 2026-06-27: Messages remains the operational queue owner, while tabs, filters, bulk actions, pending/scheduled/failed/sent cards, readiness badges, queue actions, and recovery assistant surfaces now use shared spacing/radius/size/alpha tokens. Queue callbacks, selection behavior, and tab ownership are unchanged.
- Completed slice 2026-06-28: Messages compact-phone Roborazzi baselines now cover needs-review, needs-review large-font, failed recovery large-font, and loading states. The screenshots render deterministic `MessagesUiState` fixtures through `MessagesContent` only, preserving ViewModel refresh, filtering, selection, approval, retry, rejection, and navigation behavior.
- Completed slice 2026-06-27: Settings remains the global configuration route, while account identity, preferences, credentials, automation mode, quiet hours, channel blackouts, sync/data links, legacy notice, sign-out confirmation, snackbar, and dividers now use shared spacing/radius/size/alpha tokens. Credential saves, permission-gated sync, navigation links, and sign-out orchestration are unchanged.
- Completed slice 2026-06-27: Analytics remains the reporting dashboard and export entry point, while stats, loading, report cards, monthly bar chart, relationship distribution, health, growth metrics, and neglected-contact rows now use shared spacing/radius/size tokens and theme semantic colors. Activity History stays linked instead of duplicating raw audit rows.
- Completed slice 2026-06-27: Activity History remains the secondary audit-trail owner, while search/filter spacing, loading/empty/error states, log cards, metadata rows, warning severity color, and contextual route actions now use shared spacing and semantic color tokens. Analytics and Settings may link here but do not duplicate raw audit rows.
- Completed slice 2026-06-27: Gift Advisor remains a contextual contact tool, while budget stats, AI suggestions, suggestion cards, history rows, delete affordance, feedback controls, record dialog, empty/error states, and loading rhythm now use shared spacing/radius/size/alpha/elevation tokens. Gift save/delete/suggestion behavior and validation are unchanged.
- Completed slice 2026-06-27: Style Coach remains the writing-style training/history owner, while training input, manual/auto analyze controls, progress indicators, status message, learned-profile card, empty history, and history snapshots now use shared spacing/radius/size/alpha/elevation tokens. Manual parsing, analysis callbacks, busy states, and profile/history rendering are unchanged.
- Completed slice 2026-06-27: Memory Vault remains a contextual contact tool, while prompt chips, category chips, add-note form, note rows, pinned state, empty/error/loading states, and note metadata now use shared spacing/size/alpha/elevation tokens. Prompt selection, note validation, add, pin/unpin, delete, and back behavior are unchanged.
- Completed slice 2026-06-27: Chat History remains a contextual read-only sent-message route, while loading, empty, error, back navigation, message cards, timestamp metadata, and list rhythm now use shared spacing tokens with focused coverage. It stays a merge candidate for a future Contact Detail history tab, but no behavior or data ownership changed.
- Completed slice 2026-06-27: Shared cards, avatars, health indicators, and shimmer skeletons now consume spacing/size/alpha/fraction tokens instead of raw border widths, integer dp conversion, raw grays, or ad hoc health thresholds. Screen and shared-component token scans are clean for the reviewed patterns.
- Expand Roborazzi screenshot coverage beyond the Splash/Auth/Onboarding/Home/Messages compact-phone pilots, including typical-phone and high-risk Hindi variants.
- Add light/dynamic theme decision after dark-theme redesign is stable.
- Run a final screen-local token scan after each UI slice and document any intentional exceptions.
- Split oversized screens after behavior is covered by tests: Messages, Contact Detail, Events, Settings, Gift Advisor, Wish Preview, Home, and AI Doctor.
- Re-evaluate Analytics as a primary bottom-nav item after first screenshot/usage review.
