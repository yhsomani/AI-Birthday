# Project coverage and verification report

[Knowledge home](README.md) · [Evidence register](evidence-register.md) · [Master mind map](master-project-mindmap.md) · [Verification notes](verification/README.md)

**Snapshot:** Git HEAD `20a598e9789a19e2a35a2b693e63c098473cc68d`, reviewed 2026-11-25. The documentation describes the inspected checkout and source, not a deployed product. The working tree was already dirty; existing `.gitignore`, shell golden and Message Studio test changes were left untouched by this documentation assignment.

## Inventory coverage

| Inventory | Count | Counting boundary |
|---|---:|---|
| Tracked repository files | 568 | `git ls-files` at the recorded HEAD |
| Tracked Markdown files | 220 | All repository paths ending `.md` |
| Tracked Dart files | 180 | Includes tests/generated Dart; not a measure of app surface |
| Tracked TypeScript/TSX files | 35 | Backend and Hosting source/test/config files |
| Tracked Kotlin files | 5 | Android app source tree |
| Feature hierarchy | 82 | Canonical `feature-registry.json` nodes; 76 implemented, 3 partial, 3 not implemented |
| Master graph nodes | 269 | 187 locally defined graph nodes + 82 imported feature nodes; IDs are unique |
| Master graph edges | 317 effective | 236 explicit cross-system edges + 81 registry parent edges |
| Top-level branches | 20 | Required project ecosystem branches |
| User journeys | 7 | J-01 through J-07; the separate sender control plane is not a Flutter user journey |
| Screens/pages | 14 | 9 mobile surfaces and 5 public Hosting pages |
| Interaction patterns | 30 | ACT-001 through ACT-030; grouped patterns, not every pixel-level tap target |
| Business rules | 25 | BR-001 through BR-025; source rules and approved target differences are labeled |
| Backend exports | 20 | 18 Firebase callable exports and 2 scheduled exports |
| Local database tables | 6 | Drift schema v6: Persons, Birthdays, MessageDrafts, ReminderSettingsEntries, DeliveryEvents, Jobs |
| Firestore path templates | 28 | Source-defined path families; not documents, indexes, or deployed collections |
| End-to-end data flows | 8 | FLOW-001 through FLOW-008 |
| State families | 7 | STATE-001 through STATE-007 |
| External integrations | 13 | EXT-001 through EXT-013 |
| Failure/recovery paths | 8 | ERR-001 through ERR-008 |
| Flutter test files | 72 | Tracked `test/**/*_test.dart` paths |
| Functions test files | 14 | Tracked `backend/functions/test/*.test.ts`; includes 2 emulator suites |
| Hosting test files | 3 | Tracked `backend/hosting/test/*.test.mjs` |
| Total listed test files | 89 | Sum of above; emulator tests are included, not double counted |
| Knowledge-base Markdown | 29 | Current workspace files under `docs/knowledge/`, including this report and other untracked task deliverables |

Counts describe the inspected inventory and do not establish feature completeness, quality, passing tests or deployment. The 82 features are individual source-mapped registry records; the 30 interactions are grouped user-visible patterns rather than an exhaustive control inventory.

## Architecture and product boundaries established from source

- The Flutter application is Android-first in this checkout, with a four-destination shell (Today, People, Calendar, Activity), onboarding and detail routes, and a separate Settings route. The route catalog has 10 route patterns.
- The local data boundary is Drift/SQLite schema version 6. Cloud backup is opt-in and UID-scoped in source; it is not described as end-to-end encrypted.
- AI execution is local orchestration through BYOK Gemini or an eligible Gemini Nano provider, gated by application entitlement. Studio/job state is locally persisted.
- Manual WhatsApp/SMS/share handoff is not equivalent to carrier or recipient delivery. Explicit user confirmation is a separate state.
- Firebase Functions contain an additional control plane. Flutter uses purchase verification; public Hosting uses deletion callables. No mobile sender registration/claim/arm caller was found.
- No tracked Flutter `ios/` application target or Flutter web target was identified. `backend/hosting/` is a separate web application.
- Automatic SMS and automatic preparation remain default-disabled or incomplete as end-to-end capabilities; the Play manifest does not declare `SEND_SMS`.

## Browser and device observations

- A local preview of `backend/hosting/` rendered the home, privacy, terms, support and deletion routes. Missing release/runtime configuration was displayed; deletion controls were disabled. This was a local Hosting preview, not production Hosting and not a Flutter web build.
- No production endpoint, Firebase project, Play purchase, SMS send or real user data was accessed.
- Prior Android emulator evidence showed the installed app and onboarding screen. Further shell-injected UI actions were blocked by emulator permissions, so this is not end-to-end validation of later journeys.
- Browser inspection does not validate the native Flutter interface, platform permissions, alarms, Nano availability, carrier delivery or mobile accessibility.

## Validation performed for this documentation change

The existing dependency-free documentation validator was run after the inventories were completed:

```text
python3 docs/knowledge/tools/validate_knowledge.py --format json
```

It checks Markdown fences, relative Markdown links, registry JSON, source/test reference paths, feature IDs and parent cycles. The machine graph was separately checked for JSON parsing, globally unique IDs, valid node references and evidence/detail paths.

**Result:** passed with 29 Markdown files scanned, 776 relative links checked, 25 Mermaid blocks detected, 82 feature nodes and zero validator errors. The graph check found 269 unique IDs, 236 explicit edges, 81 imported feature-parent edges, zero unresolved references and seven well-formed journey-matrix rows. `git diff --check` reported no whitespace errors.

No application source, runtime configuration, dependency manifest, database data, infrastructure or deployment file was changed for this reverse-engineering deliverable. Application tests/builds were not rerun as this turn's scope was documentation-only; test-suite existence and historical execution are not presented as current pass evidence.

## Explicit evidence gaps

- No current-head external CI run or Firebase/Hosting deployment was inspected.
- No production service configuration, Firestore production enforcement, subscription purchase, account deletion, or live sender integration was verified.
- No end-to-end physical-device journey or SMS transmission was tested in this documentation phase.
- Business model, market demand, supported locales, regulatory applicability, data-retention duration, support/incident owner, release authorization and service objectives remain unverified.
- Public Hosting capability claims conflict with the source gates and missing platform/deployment evidence; see [EV-009](evidence-register.md).
- Source-level migrations, backup failure semantics, Person-model duplication and person/occurrence transaction ownership remain documented risks rather than resolved findings; see the [gap register](gaps/README.md).

## Related inventories

- [Feature registry](feature-registry.json)
- [Journey traceability](journeys/traceability.md) · [CSV traceability matrix](traceability-matrix.csv)
- [Screen/interaction inventory](screen-interaction-inventory.md)
- [API/backend map](api-backend-map.md) · [data-flow map](data-flow-map.md)
- [Data model](data-model/README.md) · [cross-dependency graph](cross-dependency-graph.md)
- [Implementation gaps](gaps/README.md) · [evidence register](evidence-register.md)
