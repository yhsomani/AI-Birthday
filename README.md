# AI-Birthday

AI-Birthday is a **local-first Android app that helps you remember people's birthdays and send personal wishes**. It keeps your birthday records on the device, reminds you in time, helps you draft a message (manually or with AI you control), and hands the message to WhatsApp, Messages/SMS, Instagram, Snapchat, LinkedIn, or the native share sheet **for you to review and send**. WhatsApp and SMS open pre-filled; social apps use their targeted Android text-sharing entry point where supported. It never claims a message was delivered unless you confirm it.

> **Status (working tree, 2026-11-28).** `flutter analyze` is clean; the full Flutter suite passes (743 tests, excluding goldens) and both shell goldens pass against the redesigned navigation. Android/Kotlin compilation could not be run because this environment has no Java runtime. Firebase Functions and Hosting verification counts below are from the earlier recorded snapshot; deployment is not evidenced. See [IMPLEMENTATION.md](IMPLEMENTATION.md) for verification limits.

## Main capabilities

| Area | What the current source provides |
|---|---|
| People | Create, edit, search, filter, soft-delete with undo; CSV and Android-contacts import with per-row review; CSV export |
| Birthdays | Calendar-date occurrences with per-person timezone and a per-person Feb 29 choice (Feb 28 or Mar 1) |
| Reminders | Local notifications with exact alarms, quiet hours, re-planned at app start and on resume |
| Message Studio | Autosaved drafts, durable AI jobs, tone and length controls, manual writing always available |
| AI (entitlement-first) | Bring-your-own Gemini key or on-device Gemini Nano, both only with an active app entitlement; bounded timeouts and typed failures |
| Delivery | WhatsApp and SMS composer with pre-filled messages; targeted Android text-sharing handoffs for Instagram, Snapchat and LinkedIn; share/copy fallbacks; explicit "I sent it" confirmation; Activity history |
| Account (optional) | Google sign-in, Play Billing with server-side verification, opt-in cloud backup and restore (owner-scoped Firestore, **not** end-to-end encrypted) |
| Backend and site (separate) | Firebase Functions (purchase verification, account deletion, a sender-coordination control plane with no mobile caller) and a static Hosting site (privacy, terms, support, account deletion) |

Automatic SMS and automatic message preparation are **disabled by default and not available as end-to-end features**. Supported platform: **Android** (Flutter; flavors `play` and `personal`). There is no iOS target in this repository.

## Technology stack

Flutter and Dart (`>=3.12.0 <4.0.0`), Riverpod, go_router, Drift/SQLite (schema v7), flutter_secure_storage, Firebase (Auth, App Check, Firestore, Functions), Google Play Billing (`in_app_purchase`), Kotlin native layer (notifications, alarms, Gemini Nano bridge, gated SMS adapter), TypeScript backend (Functions, Vitest) and a Vite/TypeScript Hosting site. Exact versions: [pubspec.yaml](pubspec.yaml), [backend/functions/package.json](backend/functions/package.json), [backend/hosting/package.json](backend/hosting/package.json).

## Repository structure

```text
lib/                  Flutter app: app/ (shell, router, providers), core/ (database, config, security, logging),
                      features/<module>/ (people, birthdays, reminders, calendar, dashboard, message_studio, ai,
                      delivery, jobs, history, settings, auth, subscription, sync, onboarding), shared/ (design system)
android/              Gradle build, flavors, Kotlin native code
test/, integration_test/   Dart unit, widget, end-to-end and golden tests
backend/functions/    Firebase Functions (TypeScript) and Vitest suites
backend/hosting/      Public Hosting site (Vite/TypeScript) and Node tests
backend/              firebase.json, Firestore rules and indexes
docs/graph/           Machine-readable feature registry, project graph, traceability, migration record, validators
.github/workflows/    CI
```

## Quick start

Prerequisites, toolchain versions and every command with its caveats are in [OPERATIONS.md](OPERATIONS.md). In short:

```bash
flutter pub get
flutter run --flavor personal            # or --flavor play
flutter analyze && flutter test --exclude-tags golden
(cd backend/functions && npm ci && npm run check)
(cd backend/hosting && npm ci && npm run check)
python3 docs/graph/tools/validate_docs.py   # documentation, registry and graph validation
```

Google sign-in, Firebase and Play Billing require project-specific configuration (SHA-1 fingerprints, `google-services.json`, `FIREBASE_WEB_API_KEY`); see [OPERATIONS.md §2](OPERATIONS.md#2-local-development) and [§13](OPERATIONS.md#13-play-billing-and-purchases-setup). No secrets belong in the repository.

## Documentation

| Document | Purpose |
|---|---|
| [docs/PRODUCT.md](docs/PRODUCT.md) | Single source of truth for intended behaviour: vision, users, journeys, features, requirements, UX, business rules, decisions and open decisions |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System, data and API architecture, data flows, integrations, ADRs |
| [docs/SECURITY.md](docs/SECURITY.md) | Trust boundaries, privacy, access control, threat model, security gaps |
| [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md) | Verified implementation status, issue register, tests, technical debt, release readiness |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Setup, build, test, database migrations, CI/CD, deployment, runbooks, documentation maintenance |
| [docs/PROJECT_MINDMAP.md](docs/PROJECT_MINDMAP.md) | Generated master graph: hierarchy, journeys, navigation, API and module dependencies |
| [docs/AUDIT_AND_READINESS_REPORT.md](docs/AUDIT_AND_READINESS_REPORT.md) | Phase 1–4 product reassessment, consolidation record, and implementation readiness sign-off |
| [backend/README.md](backend/README.md), [backend/hosting/README.md](backend/hosting/README.md) | Package-level guides for the Functions and Hosting packages |
| [docs/graph/](docs/graph/) | Supporting feature registry, project graph data, traceability matrix, documentation migration record |

Source code and execution evidence decide what is implemented; PRODUCT.md decides what is intended; discrepancies are reported in IMPLEMENTATION.md. Older documentation (blueprints, audits, archives, design notes) was consolidated into these documents; the removed files remain in Git history at commit `c4aace16`, and [docs/graph/migration-matrix.csv](docs/graph/migration-matrix.csv) records where each preserved item went.

## License

No `LICENSE` file is present in this repository. Licensing is an open decision (see [PRODUCT.md §14](PRODUCT.md#14-open-decisions-and-unresolved-questions)). Bundled fonts (Outfit, Bricolage Grotesque) are under the SIL Open Font License; their texts are in [assets/fonts/](assets/fonts/).
