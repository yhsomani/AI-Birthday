# AI-Birthday — Source-linked knowledge portal

> [Project home](../../README.md) · [Product intent](../../SSOT.md) · [Architecture baseline](../../ARCHITECTURE.md) · [Feature registry](feature-registry.json)

**Evidence snapshot:** `main` at `75b9d91aadcc1c4136fdcb2c06340d3744bc50a4` (2026-10-14 review). The registry currently describes **50 nodes**, including the application root, domains, and child capabilities. This is a **source-review baseline**, not a claim that 50 features were tested in production or that every line was individually audited.

## Start here

| Question | Read |
|---|---|
| What does the product do? | [Product requirement / SSOT](../../SSOT.md) |
| What exists, down to micro / nano capabilities? | [Functional tree and feature contracts](features/README.md) |
| Which UI screen do I open, and what happens next? | [Navigation and user journeys](journeys/README.md) |
| What calls what? Where is data stored? | [Architecture and data lineage](architecture/README.md) |
| What is not done, contradictory, or unverified? | [Gap and improvement register](gaps/README.md) |
| How are findings linked to source and tests? | [Machine-readable registry](feature-registry.json) |
| How do we update this documentation? | [Maintenance guide](maintenance/README.md) |
| Which exact validations and business rules apply? | [Business rules](business-rules/README.md) |
| How do requirements map to functions and tests? | [Requirement traceability](traceability/README.md) |
| Which APIs and external systems interact? | [Integrations and callable APIs](integrations/README.md) |
| Who owns local/cloud data and how does it flow? | [Data model and lineage](data-model/README.md) |
| What are the atomic responsibilities of complex areas? | [Deep feature contracts](features/deep-dives/README.md) |
| Where are all declared routes and deep links? | [Navigation architecture](navigation/README.md) |
| What has CI actually verified, and what is blocked? | [Verification and coverage](verification/README.md) |

## Product boundary: important distinction

**Mobile product.** The active Flutter application is a local-first birthday manager with a user-reviewed AI/manual message studio. The high-level target cycle in [SSOT.md](../../SSOT.md) is:

```text
People → Birthday → Reminder → Prepare → AI/Manual → Review → Send → History
```

**Native integration.** Kotlin implements Android-specific notifications and Gemini Nano platform access.

**Cloud backup, purchases and identity.** The Flutter app has optional Firebase-account operations and verification integrations; none is a prerequisite for basic local contact entry.

**Additional backend product surface.** `backend/functions/` also contains a separately documented *Birthday Autopilot* server coordination control plane, and `backend/hosting/` provides public/legal/deletion routes. Their READMEs explicitly say production deployment is not evidenced. Do **not** equate the coordination capability with the current mobile app's WhatsApp workflow; see [architecture](architecture/README.md) and [gaps](gaps/README.md).

## Product capability map (containment, not execution)

```mermaid
flowchart TD
  A["AI-Birthday"]
  A --> P["People & birthday lifecycle"]
  A --> X["Experience & navigation"]
  A --> M["Compose and deliver"]
  A --> O["Operations & accounts"]
  A --> B["Additional backend surfaces"]
  P --> P1["People CRUD & import/export"]
  P --> P2["Recurrence & rollover"]
  P --> P3["Reminders & calendar"]
  X --> X1["Onboarding & dashboard"]
  X --> X2["History & settings"]
  M --> M1["Message Studio / drafts"]
  M --> M2["AI: BYOK or Nano"]
  M --> M3["WhatsApp/SMS/Share handoff"]
  O --> O1["Google authentication"]
  O --> O2["Billing & entitlement"]
  O --> O3["Cloud backup & durable jobs"]
  B --> B1["Firebase coordination control plane"]
  B --> B2["Public site / deletion"]
```

A feature tree answers **what contains what**; the [journey](journeys/README.md) and [architecture](architecture/README.md) diagrams answer **what calls what**.

## Implementation and evidence legend

| Label | Meaning |
|---|---|
| `implemented` | Implementation was located in repository source. It is **not** a guarantee that every environment works. |
| `partially-implemented` | Only a verified portion of the desired capability was identified. |
| `planned/documented-only` | Requirement or historical plan without adequate implementation evidence. |
| `proposed` | Optional recommendation made by this analysis, not an approved requirement. |
| `source-reviewed` | Code and test locations were read or their existence cross-checked; tests not executed locally in this session. |
| `CI observed` | A published GitHub Actions run was inspected; does not establish physical-device or deployment correctness. |

The latest `main` CI run inspected at this snapshot [completed successfully](https://github.com/yhsomani/AI-Birthday/actions/runs/38461085640) for commit `75b9d91`. Do not promote that result to evidence of live Play Billing, real WhatsApp sending, Google cloud provisioning, device reboot alarms, or deployed Hosting.

## Repository index

```text
lib/main.dart                    Flutter entrypoint
lib/app/                       App scope, navigation, providers, theme
lib/core/                      SQLite, platform/security/logging primitives
lib/features/                  Mobile product capabilities
lib/shared/, lib/ui/           UI widgets and styling
android/app/src/main/          Kotlin and native Android configuration
backend/functions/             Separate Firebase control plane + verification
backend/hosting/               Web legal/help/deletion frontend
test/                          Dart unit/widget/E2E harness and test suites
backend/functions/test/        Backend Vitest & emulator tests
backend/hosting/test/          Hosting Node tests
docs/archive/                  Historical snapshots; not current authority
docs/audit/                    Previous investigations; status may be stale
```

## Authority and limitations

- For *intended* product behavior use [SSOT.md](../../SSOT.md); for *implemented* behavior inspect source at the cited path; for *observed tests* consult the relevant CI run.
- Historical `PROJECT.md` milestone labels and archived audit completion statements are not automatically current.
- This documentation was produced using remote source inspection, not a local Flutter/Android/emulator run.
- Verification and unresolved distinctions are recorded in [gaps](gaps/README.md). Avoid inferring production-readiness from source presence.

**Next step:** select a node in [the feature tree](features/README.md), follow the linked Dart/Kotlin/TypeScript file, and use the associated journey and tests to verify the behavior in an appropriate runtime.
