# Phase 2 — Repository Inventory

**Commit:** `ea475e0135a99d5dbae9891340c4a554f2e5fe15`  
**Source:** Recursive Git tree for `main` at the reviewed commit.  
**Method:** Counts derived from GitHub's recursive tree response; paths are repository evidence, not a claim that every file's contents were reviewed.

## Repository size

| Area | File count |
|---|---:|
| Entire repository (blobs) | 425 |
| `lib/` Flutter/Dart application | 96 |
| `android/` Android platform | 27 |
| `backend/` Firebase Functions + Hosting | 76 |
| `docs/` documentation and screenshots | 89 |
| `test/` test sources and image artifacts | 104 |
| `firebase/` reference/native integration files | 4 |

The recursive tree contained 623 entries: 425 file blobs + 198 directory trees.

### Documentation composition

| Type under `docs/` | Count |
|---|---:|
| Markdown | 65 |
| JSON | 1 |
| Mermaid | 2 |
| PNG screenshots | 20 |
| Python validator | 1 |
| **Total** | **89** |

### Test composition

| Type | Count |
|---|---:|
| Dart files under `test/` | 84 |
| Dart files named `*_test.dart` | 72 |
| PNG test/golden artifacts | 20 |
| Backend Functions test files | 14 |
| Hosting test files | 3 |

These are file counts, not test-case counts. The latest CI run reports a separate execution count of 459 passed and 1 failed Flutter test.

## Flutter application topology

The 96 files under `lib/` are organized around:

| Domain | Representative source |
|---|---|
| App composition and routing | `lib/main.dart`, `lib/app/app.dart`, `providers.dart`, `router.dart` |
| Core database and repositories | `lib/core/database/app_database.dart`, `drift_repositories.dart` |
| Security and credentials | `lib/core/security/`, `lib/core/logging/` |
| AI and routing | `lib/features/ai/` |
| Authentication | `lib/features/auth/` |
| Birthday lifecycle | `lib/features/birthdays/` |
| Calendar and dashboard | `lib/features/calendar/`, `lib/features/dashboard/` |
| Delivery | `lib/features/delivery/` |
| Durable jobs | `lib/features/jobs/` |
| Message Studio and drafts | `lib/features/message_studio/` |
| Onboarding | `lib/features/onboarding/` |
| People and imports | `lib/features/people/` |
| Reminders | `lib/features/reminders/` |
| Settings | `lib/features/settings/` |
| Subscription and backup | `lib/features/subscription/`, `lib/features/sync/` |
| Shared design system and localization | `lib/shared/`, `lib/l10n/` |

The repository also contains a second `Person` model path (`features/people/domain/person.dart` and `features/people/domain/models/person.dart`). Existing documentation flags this duplication; all callers and conversions still require a full audit before a canonical model can be selected.

## Android and external surfaces

- `android/` contains Gradle configuration, app flavors/manifests, Kotlin receivers and `MainActivity.kt`.
- `MainActivity.kt` registers MethodChannels for notifications, Gemini Nano, share, contacts and SMS automation.
- `backend/functions/` contains TypeScript domain, persistence, transport, service and callable/scheduled function code.
- `backend/hosting/` contains a distinct TypeScript/JavaScript website with public, privacy, terms, support and deletion routes.
- `firebase/` contains reference Kotlin files and Firebase configuration artifacts.

## Existing documentation families

- Root: README, SSOT, PROJECT, ARCHITECTURE, DESIGN and related summaries.
- `docs/knowledge/`: feature registry, feature hierarchy, architecture, journeys, integrations, data model, rules, gaps, traceability and verification.
- `docs/audit/`: subscription, AI configuration, delivery truthfulness, data integrity, UI/UX, reconstruction and remediation audits.
- `docs/archive/`: historical specifications, implementation/readiness reports and traceability snapshots.
- `docs/ui-redesign/` and `docs/ui-ux/`: screen inventory, flows, design system, interaction and QA documentation.
- `docs/product-blueprint/`: initial product discovery baseline and this Phase 2 evidence audit.

## Inventory limitations

The tree count is reproducible for the reviewed commit. It does not classify every generated file, determine whether every file is used at runtime, establish deployed resources, or prove the correctness of each implementation. The next pass must inspect the complete source and document contents by bounded module, not infer completeness from this inventory.
