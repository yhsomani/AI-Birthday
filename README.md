# RelateAI React Native

RelateAI is being migrated from the legacy Kotlin Android codebase to a React Native app that follows:

- `docs/feature-fssot.md`
- `docs/feature-roadmap-analysis.md`
- `docs/README.md`

The active React Native entrypoint is `src/App.tsx`.

## Run

```bash
npm install
npm run start
```

Then open the app with Expo on Android, iOS, or web.

AI drafting can call a secure backend endpoint when one is configured:

```bash
EXPO_PUBLIC_RELATE_AI_ENDPOINT=https://your-secure-backend.example.com/draft npm run start
```

The React Native client sends only the approved drafting payload and must not contain provider secrets. When the endpoint is missing, unreachable, or returns an invalid response, the app falls back to a review-first local template.

Email delivery can also use a secure backend endpoint for approved email messages:

```bash
EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT=https://your-secure-backend.example.com/email/send npm run start
```

The app stores only the sender email preference. Provider credentials should stay on the backend.

## Validate

```bash
npm run typecheck
npm test
npm audit --audit-level=moderate
```

## Current React Native Scope

The RN app currently includes a local-first implementation shell for the most important product workflows:

- Home next-best-action dashboard.
- Native contact import with deduplication rules.
- Events with preparation checklists, type/time filters, month view, and manual event creation for existing or new local contacts.
- Calendar import/export for relationship events.
- Contacts, contact detail, guided enrichment, tone controls, preferred channel, Memory Vault, Gift Advisor, filterable relationship timeline, and searchable Chat History.
- Manual message composer with editable local templates and a separate AI draft path.
- AI provider draft generation through a secure endpoint, with local template fallback and variants.
- Message Template Library for offline/non-AI drafts by occasion and contact tone.
- Wish preview, editing, regeneration, approval, rejection, duplicate warning, AI context preview, and platform share based manual send handoff.
- Duplicate-send guardrails that block approval until the user explicitly acknowledges same-event or similar sent/scheduled message risks.
- Channel-specific SMS, WhatsApp, and email handoff with share fallback, plus configurable provider email delivery for approved messages.
- Messages queue with review, scheduled, blocked, and sent states.
- Post-send follow-up scheduling that creates reviewable follow-up events and reminders from sent messages.
- Deep-link routing for home, events, add event, contacts, chat history, messages, review/wish preview, settings, setup, and backup entry points.
- Android static launcher shortcuts for Review messages and Add event through the Expo prebuild config plugin.
- Event reminder planning, Expo notification scheduling, and safe notification tap routing back into review surfaces.
- Goal-based Setup Wizard, Setup Check, Style Coach sample analysis, Analytics summary, encrypted file Backup/Restore, Settings, and searchable/filterable Activity History under More.
- SecureStore-backed persisted app state where supported, with versioned migration and corrupt-state recovery.
- Biometric lock via Expo Local Authentication.
- Locale files for English, Hindi, and Hinglish with locale-aware navigation, status labels, dates, currency, and language settings.

The legacy Kotlin/Gradle tree is still present during migration. Do not delete it until the React Native app has verified feature parity and replacement build/release coverage.

See `docs/react-native-migration-status.md` for the current parity status and remaining replacement work.
