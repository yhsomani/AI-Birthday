# Repository Assessment

## Scope

**VF, high confidence:** Git `main` tracks a Flutter/Dart Android app, Kotlin platform adapters, Firebase TypeScript Functions, a Vite/static Hosting site, CI, 95 maintained `lib` files, extensive tests and 153 tracked documentation files. Generated outputs, build products, dependency trees and archive/original-project copies were not treated as maintained authority. Baseline: `593f7800`.

## Artifact inventory

| Path | Type | Purpose / owner | Status / authority | Preservation/migration |
|---|---|---|---|---|
| `lib/` | Flutter application | product behavior, team-maintained | IR; current executable source | retain; requirements must map, no direct docs edits |
| `android/` | Kotlin/Gradle adapters | notifications, contacts, Nano, disabled SMS | IR | retain; product capability matrix required |
| `backend/functions/` | TypeScript server | Play verification, deletion and Autopilot coordination control plane | IR, deployment unverified; Autopilot scope approved | retain; integrate only through approved local-data boundary and staged release gates |
| `backend/hosting/` | public web | Autopilot/privacy/support/delete | IR/CF; not deployed per repo docs; public identity unresolved | preserve; approve identity/legal/support/deployment separately |
| `backend/firestore.rules` | authz config | owner-scoped backup, server-only entitlement/control | IR | retain with security tests |
| `test/`, `integration_test/` | Flutter verification | unit/widget/E2E/golden assertions | VF; current run not re-executed here | retain |
| `backend/functions/test` | Vitest/emulator | server/race/policy assertions | VF | retain |
| `.github/workflows/ci.yml` | CI/release checks | analysis, tests, audits, APK and Hosting builds | EPD/IR | retain; extend evidence gates only via approved work |
| `docs/product-blueprint/` | definition/audit | intended product SSOT/history | mixed authority and conflict | preserve; 2.0 index governs this work |
| `docs/archive`, `docs/audit`, `docs/knowledge` | legacy/evidence | historical and quick-reference | mixed/stale | retained, candidate migration/deprecation |

## History

`f72bec80` created the definitive blueprint, `60d90d31` hardened v1 and disabled/deprecated high-risk capability paths, and `593f7800` added People/Birthday/leap-day/rollback/data-ownership tests. No commit evidence alone equals stakeholder approval or production deployment.
