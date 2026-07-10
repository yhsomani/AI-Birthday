# RelateAI React Native

RelateAI is the active React Native app for the migration target. It follows:

- `docs/feature-fssot.md`
- `docs/feature-roadmap-analysis.md`
- `docs/README.md`

The active app entrypoint is `src/App.tsx`. Legacy Android/Gradle artifacts, if present in the repository, are archival migration references only and are not part of the React Native build, test, or release path.

## Run

Use Node 24.18.0 and npm 11.6.x. The repository pins both through `.nvmrc`, `.node-version`, `package.json`, and npm's strict engine check. With `nvm`:

```bash
nvm use
npm ci
npm run start
```

Then open the app with Expo on Android, iOS, or web.

AI drafting can call a secure backend endpoint when one is configured:

```bash
EXPO_PUBLIC_RELATE_AI_ENDPOINT=https://your-secure-backend.example.com/draft npm run start
```

The React Native client sends only the approved drafting payload and must not contain provider secrets. Every `EXPO_PUBLIC_` value is visible in the compiled client, so use `.env.example` only for endpoint URLs and non-secret development switches. When the endpoint is missing, unreachable, or returns an invalid response, the app falls back to a review-first local template.
Release-ready provider endpoints must use HTTPS, must not embed credentials in the URL, and must not point to localhost or private-network hosts. Local development endpoints can be tested only when explicitly allowed:

```bash
EXPO_PUBLIC_RELATE_ALLOW_LOCAL_PROVIDER_ENDPOINTS=true EXPO_PUBLIC_RELATE_AI_ENDPOINT=http://localhost:8787/draft npm run start
```

Email delivery can also use a secure backend endpoint for approved email messages:

```bash
EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT=https://your-secure-backend.example.com/email/send npm run start
```

The app stores only the sender email preference. Provider credentials should stay on the backend.

## Release Config

The React Native replacement uses `app.json` for native identifiers and permissions and `eas.json` for EAS build profiles. SMS and WhatsApp currently use user-controlled deep-link/share handoff; the RN release config blocks direct SMS, SMS inbox, phone-log, phone-number, exact-alarm, and AccessibilityService permissions.

## Validate

```bash
npm run typecheck
npm test
npm audit --audit-level=moderate
npx expo install --check
npx expo export --platform web --output-dir reports/web-export
git diff --check
npm run release:evidence -- --fail-on-blockers
```

`npm test` runs the React Native source-contract suite without per-file process isolation so local and CI validation do not depend on child `node`/`esbuild` process launches.
`npm run release:evidence` executes the typecheck, test, audit, Expo dependency, web export, and diff checks itself. It writes the ignored external artifact `reports/react-native-release-evidence.json` with command exit codes, timing and output hashes plus the exact commit, dirty-state fingerprint, lockfile hash, and Node/npm versions. Environment variables cannot mark checks passed. `--fail-on-blockers` makes missing/failed proof, dirty source, unsafe release configuration, or other blockers fail the command.

The generated report is not imported by the application and is never a prerequisite for typecheck, test, or bundle. Setup Check safely reports release evidence as unattached because operational CI/release evidence does not belong in a user runtime. GitHub CI proves the clean-checkout order, uploads the report, and attests it on protected-branch pushes.

## Current React Native Scope

The RN app currently includes a local-first implementation shell for the most important product workflows:

- Home next-best-action dashboard with explicit relationship check-in queue actions.
- First-run onboarding with setup goals, account/local mode choice, permission rationale, progress preservation, and reopen-from-Home/Settings behavior.
- Account and Privacy center with local/Google-sync mode state, checklist-backed account disconnect/local data clearing, backup-before-destructive-action guidance, permission decision tracking, denial fallbacks, and revocable manual WhatsApp handoff consent.
- Native contact import with deduplication rules.
- Events with canonical preparation checklists, next-step guidance, type/time filters that default to birthday/anniversary/custom and reveal advanced categories explicitly, month view, and manual event creation for existing or new local contacts.
- Calendar import/export for relationship events, including idempotent RelateAI calendar reconciliation that updates changed mirrored events and removes stale/duplicate RelateAI exports, plus review-first CSV/vCard event import from pasted text or selected files.
- Contacts with group/quality filters and sorting, contact detail, validated editable essentials, stale-draft review guardrails after profile/preference changes, explainable relationship health, review-only classification suggestions, explicit group/VIP/DND/cadence controls with DND and route-aware approval blocking, check-in snooze and mark-contacted actions that preserve truthful last-contact history, group default inheritance with per-contact overrides, guided enrichment with all core missing-context prompts and personalization summaries, tone controls, preferred channel, searchable/editable Memory Vault with pinning, delete confirmation, note validation, and private-note AI exclusion, Gift Advisor optional budget/suggestions/history with delete confirmation, filterable relationship timeline, and searchable Chat History.
- Manual message composer with editable local templates, privacy-context summaries, readiness validation, and a separate AI-ready/fallback draft path.
- AI provider draft generation through release-ready endpoint preflight, local template fallback, variants, local rate limiting, response content-safety/language gates, AI-disable draft review guardrails, and redacted provider observations.
- Message Template Library under More for offline/non-AI drafts by contact, occasion, and tone, with editable text and review-first draft creation.
- Wish preview, editing, recipient-specific tone impact explanation with a direct adjust path, regeneration feedback chips/custom guidance, channel body validation with SMS multipart guidance, safe test-send route checks, regeneration with user-controlled AI context exclusions, confirmation-backed time-boxed approval/rejection, duplicate warning, review-next routing, AI context preview, and approval-gated manual send handoff that offers destination-app and copy/share options before explicit mark-sent confirmation.
- Duplicate-send guardrails that block approval until the user explicitly acknowledges same-event or similar sent/scheduled message risks.
- Channel-specific SMS, WhatsApp, and email handoff through native Linking/share flows, with approved-text-only copy/share fallback, post-send user confirmation, dispatch-time route, body-length, and approval-window rechecks, plus optional explicitly revealed provider email delivery for approved messages with endpoint preflight and message-level failure recovery.
- Messages Inbox with status tabs/counts including Today and separate Failed recovery, search, channel filters, sorting, individual review-first actions, advanced opt-in bulk approve/reject/retry/revoke tools, route-aware and channel-body approval eligibility, pre-bulk channel verification guidance, partial-skip summaries, recovery guidance, approval expiry, channel, automation, and schedule-setting review guardrails, and review, scheduled, blocked, failed, and sent states.
- Post-send follow-up scheduling that creates reviewable follow-up events and reminders from sent messages.
- Deep-link routing for home, events, add event, contacts, chat history, messages, review/wish preview, settings, setup, and backup entry points, with stale message/contact recovery before opening private preview surfaces.
- Android static launcher shortcuts for Review messages and Add event through the Expo prebuild config plugin.
- Privacy-minimized localized home widget summary preview, JS-to-native widget sync, and Android Expo prebuild widget packaging, with safe tiles only for today's events and pending reviews, immutable navigation intents, and no send/delete actions.
- Event reminder planning with automation mode, quiet hours, blackout windows, notification readiness diagnostics, lock-screen-safe Expo notification payloads, idempotent notification reconciliation that updates changed reminders, clears stale RelateAI reminders, preserves unrelated scheduled notifications, notification-disable reminder-plan clearing, automation-mode queued-message consequences, explicit advanced full-auto reveal plus confirmation, and safe notification tap routing back into review surfaces.
- Goal-based Setup Wizard with localized readiness steps that omit provider email until it is chosen or configured, privacy-aware Setup Check with localized provider endpoint readiness diagnostics that keep missing email provider setup optional while manual handoff is available and surface unsafe configured endpoints, explicit readiness refresh feedback, redacted dry-run snapshots, Style Coach sample analysis with an "Improve my style" action and no main profile-history UI, Analytics dashboard, default shareable summary, and explicitly revealed confirmed CSV export, encrypted file Backup/Restore, Settings with optional explicitly revealed email provider setup, relationship group defaults, automation-mode impact counts, and queued-work consequences, and searchable/filterable Activity History with localized system activity titles/details and safe recovery navigation under More.
- SecureStore-backed persisted app state where supported, with normalized entry storage, bounded chunks for oversized entries, verified storage health metadata in Persistence and Setup Check, versioned migration, and corrupt-state recovery.
- Large-dataset contract coverage for core local RN lists and reports.
- Biometric lock via Expo Local Authentication, kept in Settings/Privacy and offered as a contextual recommendation after private notes or provider setup exist.
- Locale files for English, Hindi, and Hinglish with locale-aware navigation, Onboarding/Home dashboard controls and widget summaries/tiles, primary screen headings, Events/Event cards/Add Event, Messages, Contacts/Contact Detail static workflows, Manual Composer, Chat History, Wish Preview, and More Account/Privacy/Calendar/Reminder/Import/Template Library/Persistence/Setup Wizard summaries-details-actions/Setup Check diagnostics/Style Coach/AI Provider endpoint readiness/Analytics/Backup/Activity History workflow/title/detail/Settings controls, shared actions, native feedback for manual handoff/contact import/reminder scheduling/calendar sync/event file import, system feedback, lock/home shell copy, status labels, pluralized counts, dates, currency, and language settings.
- Source-level accessibility contract coverage for native touch targets, text inputs, tabs, and checkbox state.
- Source-level primary interaction contract coverage for core tab routing, Home/Events/Messages/Contacts/More workflows, and high-risk confirmations.
- React Native release evidence generation that executes required checks, binds their hashed results to source/toolchain provenance, records native identifiers/EAS profiles/permission policy, and keeps the artifact outside the runtime app.

Legacy Android/Gradle artifacts are not an active project surface. Any reintroduced legacy path is release drift and must be resolved before signing.

See `docs/react-native-migration-status.md` for the current parity status and remaining replacement work.
