# RelateAI Technical Rebuild Assessment

Date: 2026-07-11

Status: zero-based assessment; not a release approval or implementation specification

## Executive verdict

RelateAI should be rebuilt at the product, presentation, and application-architecture layers. It should not be rewritten indiscriminately.

The current repository is best described as a heavily tested local behavior and safety prototype, not a releasable consumer product. It has strong domain policies, defensive persistence work, backup/recovery logic, permission guardrails, and source-level release checks. It also has no real end-user UI, no production account or AI session integration, no production email service, no checked-in backend or API contract, no production observability, and no signed-device/store evidence. The checked-in product deliberately renders a JSON command console, and the checked-in production composition deliberately disables the authenticated capabilities most closely associated with the name “RelateAI.”

The architecture has also overgrown its validation harness. A 141-command protocol, a 5,951-line command runtime, a 4,144-line reducer, a 1,833-line parser, and a 1,700-line persistence decoder form a de facto monolith behind otherwise sensible folder names. Adding one feature commonly requires coordinated edits across command types, parser, catalog, runtime, reducer, persistence schema, tests, and documentation. That is not a stable foundation for a screen-rich Figma implementation or a fast-moving product team.

The recommended decision is therefore:

1. Reset the product scope to a small, evidence-driven core.
2. Build a new typed application layer and presentation layer around vertical feature slices.
3. Harvest, verify, and adapt proven policies and native ports; do not preserve the JSON harness as the product API.
4. Keep the current implementation available as executable reference behavior during migration.
5. Use a clean-cut v2 implementation if there are no production users or persisted datasets. Use a strangler plus explicit data migration if any real installed data must survive.

A total greenfield rewrite that discards all tests, backup logic, scheduling rules, and data-safety work is not recommended. A selective rebuild is.

## Assessment basis and confidence

This assessment covers the current React Native working tree, including uncommitted changes present on 2026-07-11. It does not treat historical Kotlin claims as current evidence.

Measured repository facts:

| Measure                             | Current observation                                                                                                                                           |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TypeScript source                   | 243 `.ts`/`.tsx` files, 66,939 lines                                                                                                                          |
| Production/test split               | 123 production files and 41,217 lines; 120 test files and 25,722 lines                                                                                        |
| Visible React components            | Exactly three production TSX files by enforced contract                                                                                                       |
| Command surface                     | 141 command types in [`commandCatalog.ts`](../../src/application/commandCatalog.ts)                                                                           |
| Largest production files            | `commandRuntime.ts` 5,951 lines; `relateReducer.ts` 4,144; `commandRuntimeParser.ts` 1,833; `persistenceSchema.ts` 1,700; `encryptedEntityStoreCore.ts` 1,607 |
| Source tests run during assessment  | 846 passed, 0 failed, across 123 suites                                                                                                                       |
| Coverage run during assessment      | 92.77% lines, 81.90% branches, 92.77% functions                                                                                                               |
| Static checks run during assessment | TypeScript and ESLint passed                                                                                                                                  |
| Working-tree release state          | Dirty; therefore not a candidate for blocker-free production evidence                                                                                         |

High test counts are meaningful evidence of source behavior. They are not evidence of usability, product-market fit, native-device correctness, backend readiness, operational readiness, or store acceptance.

## Current evidence versus unverified claims

| Area                    | What the repository currently proves                                                                                                                                                                                               | What remains unverified or absent                                                                                                                                                                                                                                                            |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Domain behavior         | Many deterministic policies and state transitions have direct Node tests. Review-first messaging, scheduling, duplicate prevention, permission handling, import review, backup parsing, and recovery receive substantial coverage. | Whether those rules match real user expectations or business policy. There is no validated product research, production usage, or accepted business ruleset.                                                                                                                                 |
| UI and journeys         | The temporary shell executes commands and exposes redacted state. [`temporaryUiSurface.test.ts`](../../src/config/temporaryUiSurface.test.ts) proves that product screens, styling, and assets are absent.                         | Every real end-user journey, information architecture, visual state, component behavior, discoverability, accessibility flow, and Figma implementation.                                                                                                                                      |
| AI                      | Request shaping, response validation, privacy filtering, timeout handling, and local fallback logic are tested.                                                                                                                    | A deployed AI backend, authenticated session issuer, model quality, prompt evaluation, abuse controls, quotas, cost controls, provider SLOs, and production monitoring. [`productAvailability.ts`](../../src/config/productAvailability.ts) marks the authenticated AI provider unavailable. |
| Email/account sync      | Client-side seams and failure classifications exist.                                                                                                                                                                               | Production email delivery, reconciliation service, account identity, Google sync, conflict resolution against a live account, and data deletion lifecycle. These are explicitly unavailable in the checked-in composition.                                                                   |
| Persistence             | The custom encrypted repository has extensive in-memory corruption, interruption, rollback, migration, and dirty-write tests. Data lifecycle journals are explicit.                                                                | Independent cryptographic review, real-device filesystem/keychain failure testing, long-duration stress, OS upgrade/restore behavior, key rotation, production data migration, and support recovery.                                                                                         |
| Native integrations     | Thin adapter logic, generated Android source, permission configuration, and an Android prebuild/compile script exist.                                                                                                              | Signed Android/iOS artifacts, iOS compile evidence in this assessment, physical-device behavior, OEM differences, biometric hardware states, installed-app handoff behavior, widget/shortcut parity, and reboot behavior.                                                                    |
| Testing                 | 846 source tests and thresholded coverage pass.                                                                                                                                                                                    | Component tests for a real UI, end-to-end journeys, backend contract tests, signed-device automation, accessibility automation, visual regression, soak testing, memory profiling, cold-start profiling, and production canaries.                                                            |
| Release                 | A candidate-bound evidence model, permission checks, exact toolchain checks, CI workflow, and external-evidence schema exist.                                                                                                      | A clean production report, signed builds, device smokes, store submissions, release-owner verification, privacy-policy completion, and actual CI artifacts in this checkout.                                                                                                                 |
| Security/privacy        | Client secrets are prohibited, public endpoints are constrained, data is encrypted, destructive actions are confirmed, and diagnostics are aggressively redacted.                                                                  | Threat model, security review, penetration test, cryptographic design review, backend security, privacy impact assessment, retention policy approval, incident response, and legal/store declarations.                                                                                       |
| Performance/scalability | Synthetic Node tests cover 900-record list/report work, two 10k-contact indexes, and a 600-record fake repository I/O budget.                                                                                                      | Mobile JS-thread latency, cold hydration, memory, filesystem behavior at declared 30k-record limits, large backup restore, battery impact, notification scale, and real repository paging in product journeys.                                                                               |
| Observability           | A bounded in-memory operational issue queue and persisted redacted activity records exist.                                                                                                                                         | Crash reporting, release health, traces, metrics, backend correlation, privacy-safe diagnostics export, alerts, SLOs, dashboards, and incident ownership.                                                                                                                                    |
| Business readiness      | The reset SSOT and roadmap now define a proposed persona, target segment, north-star/activation measures, discovery experiments, and a pricing hypothesis.                                                                         | Customer validation, baselines, a proven acquisition model, implemented product measurement, observed retention, willingness to pay, and proof that users want the product.                                                                                                                  |

## Current architecture

The implemented runtime is approximately:

```text
Temporary React Native command shell
               |
        createProductionRuntime
               |
   +-----------+-------------------+
   |                               |
HarnessCommandRuntime         navigation/lifecycle
   |                               |
parser + command types             |
   |                               |
domain functions + 4k-line reducer|
   |                               |
single in-memory AppState <--------+
   |
PersistenceCoordinator
   |
dirty-state diff over full AppState
   |
custom encrypted entity-file repository
   |
Expo filesystem + SecureStore key

Native contacts/calendar/notifications/biometrics/share/widget adapters
and optional AI/email provider ports sit around the application layer.
```

This design has real strengths, but the center is a global state machine and universal command router rather than cohesive use cases owned by features.

### Strengths worth retaining

- Strict TypeScript, no implicit returns, no `any`, and useful import-boundary lint rules.
- Domain production modules do not import React Native, Expo, UI, application, or state modules.
- Review-first communication and explicit confirmation are consistently modeled.
- Durable persistence is separated from reducer execution; commands generally do not report success before verified storage.
- Data clear and restore have journaled crash recovery and native reconciliation.
- Backup input, provider responses, command input, import files, and persistence records are bounded.
- Provider secrets are not expected in the client, and the default composition fails closed.
- Native adapters are generally behind injectable ports, making source tests possible.
- Tests are colocated and unusually comprehensive for failure and recovery paths.
- Release configuration blocks several high-risk permissions that are not required by the review-first product.

These are assets. They do not justify retaining the current central application shape.

## Architecture and folder-structure assessment

### Horizontal folders conceal vertical coupling

The top-level split into `domain`, `application`, `state`, `native`, `config`, and `ui` appears clean. Inside those folders, however, feature ownership is weak:

- `src/domain` is a flat collection of dozens of features sharing one broad [`AppState`](../../src/domain/types.ts).
- `src/application` centralizes almost all feature orchestration in [`commandRuntime.ts`](../../src/application/commandRuntime.ts).
- `src/state` centralizes feature mutations in [`relateReducer.ts`](../../src/state/relateReducer.ts) and all persisted validation in [`persistenceSchema.ts`](../../src/state/persistenceSchema.ts).
- Command input, output, parsing, catalog, dispatch, scope, redaction, and tests are separately encoded. Exhaustiveness checks prevent omission, but they do not eliminate semantic duplication.
- Native adapters, storage implementation, migration implementation, provider clients, and generated-widget bridges share one flat `native` folder.
- The generated native plugins are JavaScript outside the TypeScript and ESLint scope. The most complex plugin emits a substantial Android widget implementation as string templates.

The result is “layered” code that still has a high change radius. A feature team cannot own one directory and ship one vertical capability independently.

### The command harness has become accidental architecture

[`commandCatalog.ts`](../../src/application/commandCatalog.ts) lists 141 types. [`commandRuntimeTypes.ts`](../../src/application/commandRuntimeTypes.ts), [`commandRuntimeParser.ts`](../../src/application/commandRuntimeParser.ts), [`commandRuntime.ts`](../../src/application/commandRuntime.ts), and their tests reproduce the same public surface in different forms.

That protocol was useful for validating behavior without UI. It should remain a developer adapter at most. It should not be the service boundary used by every future screen because:

- JSON parsing and output redaction concerns dominate ordinary in-process calls.
- A single router owns unrelated imports, messages, analytics, settings, backup, permissions, and recovery.
- Feature return values are broad tagged unions and `Record<string, unknown>` containers rather than feature-owned view data.
- Temporary confirmation/session state accumulates in one runtime object.
- The console’s need for opaque IDs and bounded JSON pages distorts APIs intended for typed React hooks or view models.

Future screens should call typed use cases such as `ListUpcomingMoments`, `CreateDraft`, `ApproveDraft`, and `ClearLocalData`, not construct harness JSON.

### The reducer is a god object

[`relateReducer.ts`](../../src/state/relateReducer.ts) declares the cross-product action union near line 136 and does not export the final reducer until line 4,144. It owns unrelated entity lifecycle, scheduling, provider status, setup, navigation, privacy, analytics activity, and presentation selection changes.

This produces several costs:

- Domain invariants are distributed between feature helper modules, runtime prechecks, and reducer branches.
- Reviewers must understand global state interactions for local changes.
- Merge conflicts and test setup grow with every feature.
- State transitions cannot be independently versioned or loaded.
- The reducer is both a compatibility layer and a business-logic layer, making later deletion difficult.

### Folder recommendation

Move from horizontal global layers to feature slices with explicit shared kernels and infrastructure ports. A proposed target structure appears later in this document.

## State and data assessment

### Global `AppState` mixes unrelated concerns

[`AppState`](../../src/domain/types.ts) combines:

- domain records: contacts, events, memories, gifts, messages;
- derived/operational records: activity, setup checks, reminder plans, provider status;
- user configuration and privacy state;
- persistence health;
- UI state: active screen, selections, and search query.

Presentation state therefore participates in durable snapshots and dirty-state comparison. Domain records are arrays, IDs are plain strings, dates are plain strings, money is a `number`, and several important fields such as message readiness remain untyped strings. Referential integrity is repaired centrally during persistence decoding rather than being naturally constrained by aggregate repositories.

The target should separate:

1. durable domain aggregates;
2. durable user preferences and consent;
3. durable workflow/outbox state;
4. ephemeral session/navigation state;
5. derived projections and diagnostics.

### Persistence capability is not used by the application model

The repository interface in [`entityRepository.ts`](../../src/domain/entityRepository.ts) supports bounded queries, archive state, and retention. The production runtime nevertheless calls `loadState()` and hydrates the complete dataset through [`loadEntityRepositoryState`](../../src/state/entityRepositoryPersistence.ts). Product list/query commands then scan the in-memory arrays. Production code does not call repository `query`, `setArchiveState`, or `applyRetentionPolicy`; those capabilities are exercised only by repository tests and migration forwarding.

Dirty writes are more efficient than replacing every ciphertext blob, but `computeDirtyStateWrite` still compares collections across the complete previous and next `AppState`, including stable JSON conversion. The repository rewrites an encrypted manifest and the runtime repeatedly serializes state to compare persistence snapshots.

This means the architecture pays for a custom indexed repository without receiving end-to-end paging or bounded-memory behavior. At the declared limits of 10,000 records per aggregate and 30,000 total, cold hydration, full checksum verification, JSON comparison, and JS-array projections need real-device evidence. They do not currently have it.

### Custom encrypted file storage is a high-cost design commitment

[`encryptedEntityStoreCore.ts`](../../src/native/encryptedEntityStoreCore.ts) implements encryption envelopes, manifests, indexes, alternating checkpoints, rollback, migrations, retention metadata, paging, dirty writes, and key destruction in roughly 1,600 lines. The design is thoughtful and test-heavy. It is still custom security-critical storage.

Risks include:

- a large bespoke correctness and cryptographic review surface;
- no cross-process writer support;
- many encrypted files plus manifest lifecycle;
- in-memory index evaluation and manifest rewrites;
- dependence on Expo filesystem and SecureStore semantics;
- migration responsibility owned entirely by this application;
- no independent audit evidence.

Before carrying this into v2, compare it against a reviewed encrypted database option that proves encryption of database pages, journals/WAL, key lifecycle, migrations, backup exclusion, and Android/iOS behavior. Do not replace it with plaintext SQLite. Do not retain it merely because it already exists.

### Retention is designed but not operational

The repository implements archive and retention methods, but no production application call invokes them. Persisted activity and terminal messages therefore keep growing until other limits or explicit user actions intervene. A data-retention policy must be a product/privacy decision with a scheduled use case, not an unused repository capability.

## API and integration assessment

There is no backend project, OpenAPI document, API schema, authentication protocol, account model, server-side idempotency contract, quota design, deletion API, or integration environment in this repository.

The client has careful interfaces for short-lived provider sessions and bounded AI/email payloads. That is good seam design, but it is not an integration. [`providerSessions.ts`](../../src/application/providerSessions.ts) defaults to no session, while [`productAvailability.ts`](../../src/config/productAvailability.ts) marks authenticated AI, email, and Google sync unavailable.

Consequences:

- The headline AI outcome is not production-capable from the checked-in app.
- Client tests can only verify assumptions about an unspecified backend.
- Email idempotency explicitly depends on backend enforcement that is not defined here.
- Account-to-local merge, sync conflicts, deletion, and continuity have no system design.
- There is no contract test target or staging environment.

The committed core is local-first and must work without an account or AI. If discovery validates assisted composition, add an optional backend-for-frontend with versioned schemas, authenticated short-lived sessions, provider isolation, server-side rate/cost controls, idempotency, deletion, audit, and privacy-safe telemetry. Persistent accounts and cross-device sync are a separate, deferred continuity decision; they must not be smuggled into the AI session boundary.

## Security and privacy assessment

### What is strong

- Release configuration avoids direct SMS, inbox, call-log, exact-alarm, overlay, write-contact, and AccessibilityService permissions.
- Android auto backup is disabled.
- Provider endpoints reject unsafe production URLs and client secrets are prohibited.
- Private memories are excluded from AI context by policy.
- Backup and live storage use separate keys/passwords.
- Clear removes the repository master key before best-effort ciphertext cleanup.
- Failure summaries are aggressively bounded and redacted.
- Destructive and send-adjacent workflows use confirmation/revalidation.

### What is missing

- A formal threat model and abuse-case inventory.
- Data classification tied to each field and external flow.
- Independent review of custom storage and backup cryptography.
- Key rotation and cryptographic agility plan.
- Backend authentication/authorization and tenant isolation.
- Retention and deletion policy with operational execution.
- Incident response and security logging strategy.
- Privacy impact assessment and verified store declarations.
- Tests on rooted/jailbroken devices, restored devices, OS upgrades, key invalidation, and partial filesystem loss.
- A minimum-scope permission design: `app.json` still declares broad Android `READ_CONTACTS`, `READ_CALENDAR`, and `WRITE_CALENDAR`, while the reset calls for selective person import and does not commit calendar import/export. Remove those permissions when platform pickers suffice, or document and validate why each remains essential.

The current redaction strategy also reduces diagnosability. That is a reasonable default, but v2 needs structured, allowlisted error metadata rather than only generic summaries.

## Native-platform assessment

The native boundary is generally injectable and user-intent-gated. Contacts, calendar, notification, biometric, sharing, backup file, and handoff logic have source tests.

Material concerns:

- Android widget and shortcut behavior is generated by custom JavaScript config plugins. Plugin code is excluded from ESLint and TypeScript.
- The Android plugin generates substantial Java/XML source from string templates, increasing upgrade and review risk.
- The widget is Android-specific while the product claims Android and iOS as equal release platforms.
- CI config provisions Android/JDK compilation, but no equivalent iOS compile job exists in the checked-in workflow.
- Physical-device behavior remains explicitly external evidence.
- The product roadmap already classifies widgets and most shortcuts as non-core, so their native maintenance cost is hard to justify before the core product works.

Recommendation: defer widget and expanded shortcuts from the rebuild MVP. Keep native ports narrow, add platform contract tests, and prove each required capability on a supported-device matrix before restoring optional native surfaces.

## Testing assessment

### Strengths

- 846 tests currently pass.
- Coverage exceeds the configured 90/80/90 thresholds.
- Failure, corruption, stale confirmation, duplicate-send, permission denial, and recovery paths receive unusual attention.
- Domain tests are fast and deterministic enough to run as one Node suite.
- Native adapters commonly accept fake dependencies rather than hiding direct platform calls.

### Gaps and false-confidence risks

- All executed tests are source-level Node tests. They do not render real product screens or drive a packaged app.
- There is no checked-in end-to-end test command, component-testing stack, device automation suite, or accessibility automation suite.
- Several architecture/config tests assert source text or regular expressions. These are useful drift alarms but brittle substitutes for behavior.
- The 900-record product-builder test permits five seconds on desktop Node; it is not a mobile performance budget.
- The 10k test covers only two pre-indexed computations, not hydration, persistence, backup, navigation, or complete core journeys.
- Repository scale uses 600 records and an in-memory fake filesystem.
- Android prebuild compilation cannot prove iOS behavior.
- No backend exists for contract, resilience, or load tests.
- Coverage rewards the current monolith and can make deletion/refactoring expensive without proving user value.

The rebuild should retain policy tests as characterization tests, then add component, integration, contract, migration, device E2E, accessibility, and performance layers. Coverage should remain a guardrail, not the product-quality headline.

## Release and supply-chain assessment

The source-only release evidence design is stronger than average: it binds commands to toolchain/source provenance, validates package scripts, blocks dangerous permission drift, distinguishes source-only from production evidence, and requires candidate-bound external artifacts.

However, the repository has invested hundreds of lines in release-evidence generation and tests while the real UI and production backend are absent. This is an opportunity-cost warning. Release proof should be simplified around the target product once scope is reset; it should not become another product subsystem.

Remaining gaps:

- no blocker-free production evidence in this dirty workspace;
- no signed-build or store evidence committed or linked here;
- no iOS build job in the checked-in CI;
- no SBOM/provenance for signed application artifacts shown here;
- no documented owner/cadence for routine dependency and Expo/React Native upgrades;
- no rollout, rollback, staged-release, or production-health gate;
- no proof that the custom native plugins survive future Expo/React Native upgrades.

## Observability and operations assessment

[`OperationalIssueQueue`](../../src/application/operationalIssues.ts) is an in-memory bounded list. The persisted `activity` collection is a user-facing/local audit mechanism. Provider observations are local and redacted. There is no production crash reporter, event pipeline, trace context propagation to a backend, health metric, alert, or operations dashboard.

Therefore the project cannot currently answer basic production questions:

- Are users completing onboarding and the first useful action?
- Are reminders firing on time?
- Are drafts generated, approved, handed off, and confirmed?
- How often do backup restore or storage recovery fail?
- Which app version or OS has a regression?
- What is the crash-free session/user rate?
- What is AI latency, cost, fallback rate, or unsafe-output rate?
- Are privacy-sensitive errors being redacted as intended in production?

V2 needs an allowlisted observability model designed with privacy, not raw logging bolted on later. It should include short-lived operation/support IDs, crash reporting without relationship content, release/version/OS dimensions, core journey metrics, backend request correlation, SLOs, and alert ownership. Correlation identifiers must never be stable person/contact identifiers. Names, routes, dates, relationship labels, notes, message content, AI prompts/responses, backup metadata, and raw provider payloads are forbidden. Analytics remains opt-in, and consent/store disclosure must match the implemented model.

## Scalability and performance assessment

The current implementation is plausibly adequate for a small local dataset, but the declared storage limits exceed the evidence.

Primary bottlenecks:

- full-state hydration decrypts every record;
- all active feature work reads a single in-memory `AppState`;
- dirty-state detection compares complete collections;
- persistence snapshot checks serialize complete state;
- repository manifests are rewritten and scanned;
- many domain reports sort/filter complete arrays;
- archived repository records remain part of full `loadState()` reconstruction;
- retention and repository query APIs are not connected to product reads;
- a 400,000-character temporary result surface and multi-megabyte command/backup inputs can pressure the JS thread.

Required v2 budgets should include cold start, unlock/hydration, list first-content, search, draft creation, reminder reconciliation, one-record save, backup export/restore, peak memory, storage growth, and battery. Measure them on low/mid-tier supported Android and iOS devices, not only desktop Node.

## Maintainability and implementation-pattern debt

| Pattern                                    | Assessment                                                                                        | Required direction                                                                                              |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Universal command router                   | High debt. Temporary validation protocol became the application facade.                           | Keep only as a development/test adapter over typed use cases.                                                   |
| Single global reducer                      | High debt. Unrelated feature invariants and UI state share one transition file.                   | Feature-owned aggregates/use cases; small session store for UI only.                                            |
| Broad `AppState` snapshots                 | High debt. Persistence, diagnostics, navigation, and domains are coupled.                         | Split durable aggregates, workflow state, preferences, projections, and ephemeral state.                        |
| Repeated command schemas                   | High debt. Type, parser, catalog, runtime, metadata, output union, and tests must agree manually. | One schema source where external serialization is truly needed; ordinary UI calls remain typed in-process.      |
| Flat feature folders                       | Medium-high debt. Easy to start, hard to own at current scale.                                    | Vertical slices with explicit public APIs and dependency rules.                                                 |
| Custom encrypted repository                | High risk/cost, despite good tests.                                                               | Independent review and explicit storage ADR before adoption in v2.                                              |
| Plain string IDs/dates/status details      | Medium debt. Invalid states remain representable.                                                 | Branded IDs/value objects, clock/calendar abstraction, and typed error/readiness codes.                         |
| Implicit clocks in domain defaults         | Medium debt. Many functions default to `new Date()`.                                              | Inject clock/time-zone context at use-case boundaries.                                                          |
| Persisted navigation/search                | Medium debt. Presentation changes trigger durable data work.                                      | Keep navigation and transient search out of domain persistence.                                                 |
| In-memory confirmation sessions            | Medium debt. Safe after process death because actions fail, but UX state disappears.              | Explicit workflow state policy: durable only where recovery is required; documented ephemeral expiry elsewhere. |
| Regex/source architecture tests            | Medium debt. Helpful alarms, brittle semantics.                                                   | Prefer executable boundary tests, config introspection, and packaged-app checks.                                |
| Local generic errors only                  | High operational debt. Privacy-safe but not diagnosable at scale.                                 | Structured allowlisted error taxonomy and privacy-safe telemetry.                                               |
| Unused repository retention/query features | Medium-high debt. Complexity exists without product benefit.                                      | Connect through real use cases or delete.                                                                       |
| Optional widget/shortcuts before core UI   | Medium product/technical debt. Native maintenance precedes validated value.                       | Defer until usage evidence justifies them.                                                                      |

## Technical-debt register

Severity meanings:

- **Critical:** blocks a truthful product or production claim.
- **High:** creates material correctness, data, security, scale, or delivery risk.
- **Medium:** raises ongoing cost or future defect probability but can be scheduled.

| ID    | Severity    | Debt                                                     | Evidence and consequence                                                                                                                                                                                                  |
| ----- | ----------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TD-01 | Critical    | No real product UI                                       | [`App.tsx`](../../src/App.tsx) renders only [`MinimalFunctionalShell`](../../src/app/MinimalFunctionalShell.tsx); the test suite forbids product components and assets. No user journey can be accepted.                  |
| TD-02 | Critical    | Previously advertised AI/account services absent         | Authenticated AI, email, and Google sync are false in [`productAvailability.ts`](../../src/config/productAvailability.ts). The assisted-writing differentiation remains unproven.                                         |
| TD-03 | Critical    | No production evidence or operations                     | Signed/device/store evidence is external; monitoring is absent; current tree is dirty.                                                                                                                                    |
| TD-04 | High        | God application runtime                                  | 5,951-line runtime routes nearly all features and owns transient workflow state. Change radius and regression risk are high.                                                                                              |
| TD-05 | High        | God reducer and state                                    | 4,144-line reducer plus global `AppState` mix business, integration, persistence, and UI concerns.                                                                                                                        |
| TD-06 | High        | Persistence/application mismatch                         | Repository paging and retention exist but production hydrates/scans full state. Complexity does not produce end-to-end scale.                                                                                             |
| TD-07 | High        | Custom security-critical storage                         | Strong tests do not replace independent crypto/filesystem review or device evidence.                                                                                                                                      |
| TD-08 | High        | Missing API/system contract                              | No backend, auth protocol, API schema, idempotency service, deletion flow, staging environment, or contract tests.                                                                                                        |
| TD-09 | High        | No production observability                              | In-memory issue queue cannot support release health, incident response, or business measurement.                                                                                                                          |
| TD-10 | High        | Test pyramid stops below product                         | No real UI component/E2E/accessibility/device suite and no backend tests.                                                                                                                                                 |
| TD-11 | Medium      | Superseded documentation remains in the active tree      | The reset replaced the former root technical omnibus and added authority banners/indexes, but the previous 28-area FSSOT, roadmap, and historical ADRs still require archive/deletion after product-owner history review. |
| TD-12 | High        | Scope far exceeds validated MVP                          | 28 indexed feature areas (including 11A/11B) and 141 commands exist before a usable core journey or business measurement.                                                                                                 |
| TD-13 | Medium-high | Retention is not invoked                                 | Archive/retention implementation exists only behind unused repository methods, leaving growth and privacy behavior undefined.                                                                                             |
| TD-14 | Medium-high | Native plugin maintenance/parity                         | Large Android source templates are generated by untyped/unlinted JavaScript; iOS parity is not demonstrated.                                                                                                              |
| TD-15 | Medium      | Weak value types and temporal model                      | Strings and numbers represent IDs, ISO instants, local dates, currency, and readiness across a broad model.                                                                                                               |
| TD-16 | Medium      | Release-proof machinery is disproportionate              | The evidence subsystem is robust but consumes substantial maintenance before core product delivery.                                                                                                                       |
| TD-17 | Medium      | Repository and migration complexity precede proven scale | Multiple legacy formats, dual-read migration, custom indexes, archive metadata, and rollback generations enlarge the permanent support surface.                                                                           |
| TD-18 | Medium      | Non-descriptive project history                          | Recent commit subjects such as “update” and “ok” offer little decision traceability; ADRs cannot compensate for poor change intent.                                                                                       |

## Documentation inventory and reset plan

### Current documents

| Document                                                                        | Decision                                                                                  | Reason                                                                                                                                                 |
| ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [`SSOT.md`](../../SSOT.md)                                                      | Keep as the single product authority                                                      | The reset replaced the 1,262-line historical Kotlin document with a business/end-user SSOT, explicit non-goals, evidence gates, and governance.        |
| [`product-reset/README.md`](README.md)                                          | Keep as the deliverable index                                                             | It maps every requested audit output but is subordinate to the SSOT for product decisions.                                                             |
| [`current-product-assessment.md`](current-product-assessment.md)                | Keep as dated audit evidence                                                              | It records current capability findings, five-dimension scores, business-logic defects, and portfolio decisions.                                        |
| [`user-experience-and-ideal-product.md`](user-experience-and-ideal-product.md)  | Keep as supporting journey analysis                                                       | It expands current and ideal flows, remains subordinate to the SSOT, and must be reconciled whenever the SSOT changes.                                 |
| [`product-vision-and-roadmap.md`](product-vision-and-roadmap.md)                | Keep as the evidence-gated sequence                                                       | It sequences discovery, Figma proof, delivery, monetization, and expansion without becoming a second product scope.                                    |
| [`feature-fssot.md`](../feature-fssot.md)                                       | Archive or delete after product-owner history review                                      | Its superseded banner prevents current authority, but at 2,126 lines and 28 indexed areas it remains a costly aspiration catalog.                      |
| [`feature-roadmap-analysis.md`](../feature-roadmap-analysis.md)                 | Archive or delete after product-owner history review                                      | Its useful historical prioritization is now incorporated into the evidence-gated reset roadmap.                                                        |
| [`react-native-migration-status.md`](../react-native-migration-status.md)       | Replace with a generated/current capability-evidence matrix, then retire after v2 cutover | Hand-maintained status prose will drift and repeats README/ADR claims.                                                                                 |
| [`README.md`](../../README.md)                                                  | Keep, substantially shorten                                                               | Keep setup, commands, architecture pointer, and known unavailable services. Move release theory and product scope elsewhere.                           |
| [`docs/README.md`](../README.md)                                                | Keep as the strict index                                                                  | It now identifies one authority per question and separates product direction from implementation evidence.                                             |
| ADR 0001–0004                                                                   | Remove from active tree or move to a clearly marked historical archive                    | They describe the removed Kotlin architecture and refer to nonexistent modules.                                                                        |
| [`ADR 0005`](../architecture/adr/0005-react-native-replacement-architecture.md) | Supersede with the v2 architecture ADR                                                    | It accurately describes the current replacement, including assumptions that this assessment rejects.                                                   |
| [`ADR 0006`](../architecture/adr/0006-encrypted-entity-file-repository.md)      | Retain provisionally, reopen decision                                                     | It is the best technical rationale in the repository, but v2 storage selection and independent review remain open.                                     |
| [`release-checklist.md`](../operations/release-checklist.md)                    | Keep, split and simplify                                                                  | Separate automated source gates, signed-device matrix, store/policy signoff, and rollback/monitoring gates. Remove temporary-console wording after v2. |
| [`privacy-and-permissions.md`](../security/privacy-and-permissions.md)          | Keep, rewrite around the final data map and threat model                                  | It is useful, but many entries are conditional or release-owner reminders rather than verified final declarations.                                     |

### Duplicate or low-value documentation patterns

- README, migration status, ADR 0005, and docs index repeat the active architecture and external-evidence caveats.
- Feature FSSOT and roadmap repeat feature purpose, prioritization, and ideal workflow.
- The former SSOT repeated architecture, feature inventory, testing, debt, folder analysis, and troubleshooting for a removed system; the reset replaced it rather than maintaining both truths.
- Long prose inventories duplicate facts that can be generated from command catalogs, config, tests, or release reports.
- Test details are repeated in docs without linking to a stable, generated evidence artifact.

### Remaining missing documents

Create only documents with a named audience, owner, and update trigger:

The product SSOT and reset roadmap now exist. Implementation should not begin until it creates the remaining artifacts needed by the approved stage:

1. **System context and trust boundaries:** client, OS services, backend, providers, stores, data flows, and threat actors.
2. **V2 architecture:** feature ownership, dependency rules, use-case contracts, data ownership, and decision records.
3. **Data model and migration plan:** schemas, invariants, retention, deletion, backup compatibility, rollback, and installed-data decision.
4. **API contract:** versioned OpenAPI or equivalent schemas, auth/session lifecycle, idempotency, errors, quotas, deletion, and observability fields.
5. **Security threat model and privacy impact assessment.**
6. **Test strategy and supported-device matrix:** test layers, environments, performance budgets, accessibility, and release evidence.
7. **Observability/SLO specification:** allowlisted events, redaction, dashboards, alerts, ownership, and incident linkage.
8. **Release/rollback runbook:** staged rollout, migration rollback, support response, and stop-ship thresholds.
9. **ADR index:** active, proposed, superseded, and historical decisions with explicit status.

Avoid recreating a single multi-thousand-line technical omnibus. The new business/product SSOT should be authoritative for what and why; ADRs and contracts should own how.

## Build-versus-rebuild decision matrix

Scores are relative: 1 is poor, 5 is strong.

| Option                                                                             | Speed to credible MVP | Long-term maintainability | Preserves tested safety | UX/product freedom | Data migration safety | Overall                                                                                               |
| ---------------------------------------------------------------------------------- | --------------------: | ------------------------: | ----------------------: | -----------------: | --------------------: | ----------------------------------------------------------------------------------------------------- |
| Continue and add Figma screens directly to current commands                        |                     2 |                         1 |                       5 |                  2 |                     4 | Reject. Fastest-looking path creates permanent coupling to the validation harness.                    |
| Refactor the current monolith in place before UI                                   |                     2 |                         3 |                       4 |                  3 |                     4 | Possible but slow and conflict-heavy; every extraction crosses large central files.                   |
| Selective clean rebuild in the same repository, harvesting policies/tests/adapters |                     4 |                         5 |                       4 |                  5 |                     3 | Recommended if there are no production datasets.                                                      |
| Strangler v2 beside v1 with compatibility/migration adapters                       |                     3 |                         5 |                       5 |                  5 |                     5 | Recommended if any real installed data or rollout continuity exists.                                  |
| Total greenfield rewrite including security/storage rules                          |                     3 |                         4 |                       1 |                  5 |                     1 | Reject unless the current security/storage design fails review. Too much proven safety would be lost. |

### Decision

Do not “finish the UI” on top of the current command runtime. Rebuild the application facade and feature ownership first, while turning current tests into characterization evidence.

The default recommendation is a selective clean rebuild because the repository provides no evidence of production users, released datasets, or an existing supported deployment. That assumption must be confirmed. If it is false, switch to the strangler strategy before writing v2 persistence.

## Rebuild feasibility

Feasibility is high for a selective rebuild because:

- the UI is intentionally absent, so there is little presentation code to migrate;
- backend integration is absent, so there is no deployed API compatibility to preserve;
- domain logic is pure TypeScript and well tested;
- native adapters already expose testable seams;
- dependencies are relatively small;
- production capability flags already distinguish unavailable services;
- the current harness can serve as an executable oracle during migration.

Feasibility is not yet proven because these decisions are missing:

- whether any users or production data exist;
- whether the defined 11-capability vertical-slice program is approved as written or must be narrowed before construction;
- whether authenticated assisted composition passes its discovery, quality, privacy, cost, and operating gates;
- whether later account-backed continuity solves measured demand that local encrypted backup cannot;
- target retention and deletion policy;
- storage technology after security review;
- Android/iOS support matrix;
- budget, team ownership, and release deadline.

### Assets to harvest

- occasion recurrence and scheduling policies;
- message approval, duplicate prevention, delivery-unknown, and manual-handoff rules;
- contact/event import parsing and explicit conflict review;
- privacy filtering and bounded provider response handling;
- permission-state distinction and reminder reconciliation core;
- backup format tests and lifecycle recovery scenarios;
- persistence corruption/migration characterization tests;
- native port interfaces and safe permission configuration;
- release permission drift checks;
- product availability truthfulness.

### Assets to replace or demote

- JSON command protocol as the primary application API;
- global reducer and global `AppState` as universal ownership;
- persisted navigation/search state;
- broad feature output unions and `Record<string, unknown>` view data;
- temporary shell and functional summaries;
- the aspirational 28-area legacy scope;
- optional widget/shortcut surfaces in MVP;
- historical Android documentation;
- current storage choice if independent review favors a simpler audited option.

## Target architecture

### Principles

1. Organize code around user/business capabilities, not transport layers.
2. Each feature owns domain rules, application use cases, contracts, and tests.
3. Presentation calls typed use cases; JSON exists only at real process/network boundaries.
4. Durable state is not one global snapshot.
5. Side effects use explicit ports and durable workflow/outbox records where recovery matters.
6. External systems are versioned contracts with authentication, idempotency, and observability.
7. Security, privacy, accessibility, and performance have measurable acceptance criteria.
8. Optional features do not enter the architecture until product evidence justifies them.

### Proposed source structure

```text
src/
  app/
    composition/
    lifecycle/
    routing/
  core/
    result/
    ids/
    time/
    validation/
    observability/
  features/
    people/
      domain/
      application/
      ports/
      presentation/
      tests/
    moments/
    today/
    reminders/
    compose-review/
    context/
    privacy-data/
    backup-recovery/
  platform/
    persistence/
    contacts/
    notifications/
    biometrics/
    sharing/
  presentation/
    navigation/
    design-system/
    accessibility/
  devtools/
    command-harness/
```

Only create slices that survive the product reset. Add an `integrations/ai/` boundary only if authenticated assistance reaches technical stage 5. Add account-sync or calendar adapters only after their separate product evidence gates pass. Gift, analytics export, style history, bulk actions, widgets, shortcuts, email delivery, and automation should not be empty scaffolds in v2.

### Core domain boundaries

- **Person:** identity, routes, explicit preferences, and archive state.
- **Moment:** local calendar date/recurrence, verification, source identity, and preparation status.
- **ContextEntry:** a user-authored timeline item with an explicit privacy/assistance eligibility level.
- **Draft:** editable content, review version, duplicate fingerprint, selected route, and handoff readiness.
- **ActionRecord:** content-free, user-confirmed completion or dismissal state; never a fabricated delivery receipt.
- **ReminderPreference:** user intent, lead time, quiet hours, and native reconciliation state kept distinct from OS authorization.
- **DataControlState:** consent, permissions intent/history, export envelope, restore transaction, deletion scope, and recovery journal.

Use branded identifiers, local-date and instant types, explicit time-zone conversion, typed error codes, and versioned workflow states. Keep UI labels out of domain state.

### Application layer

Expose small use-case interfaces, for example:

- `ListUpcomingMoments`
- `ImportPeopleForReview`
- `ResolveImportConflict`
- `CreateLocalDraft`
- `CreateAiDraft` (conditional on the assisted-composition gate)
- `EditDraft`
- `ApproveDraft`
- `OpenManualHandoff`
- `RecordHandoffCompletion`
- `ReconcileReminders`
- `ExportBackup`
- `RestoreBackup`
- `ClearLocalData`
- `DeleteAllUserData` (coordinates local, backup, and server scope where applicable)

Each use case owns authorization, validation, transaction boundaries, emitted domain events, and result types. A development harness may adapt JSON to those interfaces, but it must not own the business workflow.

### State and persistence

- Keep navigation, filters, text input, and temporary view state in presentation/session stores.
- Load projections/pages required by a screen rather than reconstructing every record at startup.
- Use aggregate repositories and explicit transactions.
- Store durable workflow state for scheduled/unknown delivery and data replacement.
- Use an outbox/reconciliation model for notifications and backend actions.
- Define schema migrations and backup compatibility separately.
- Make retention an invoked use case with a user/privacy-approved policy.
- Benchmark the selected encrypted storage on real devices before committing to declared limits.

### Backend/API

If authenticated assisted composition passes its gate:

- build a backend-for-frontend rather than exposing provider details to the app;
- issue short-lived authenticated sessions;
- define versioned request/response schemas and error taxonomy;
- enforce server-side quotas, cost controls, abuse controls, and idempotency;
- store the minimum data required and document deletion/retention;
- correlate client and backend operations only with short-lived operation IDs, never stable person/contact identifiers;
- support staging and contract tests;
- make local templates a first-class offline fallback.

This short-lived assistance session is not a user account or sync design. Persistent identity and encrypted continuity require their own later evidence gate, threat model, recovery model, and deletion contract.

### Security

- Complete threat modeling before persistence/backend selection.
- Commission review of encryption, backup KDF parameters, key handling, migration, and deletion.
- Keep secrets out of public build config.
- Bind approvals to immutable message versions and revalidate at side-effect time.
- Define key rotation and lost-key behavior.
- Test clear/restore/key invalidation on physical devices.
- Maintain a field-level data flow and privacy inventory.
- Retain and extend dependency scanning; add secret scanning and signed-application provenance to release gates.

### Testing

Target pyramid:

1. pure policy/value-object tests;
2. feature use-case tests with contract fakes;
3. repository migration/corruption/property/fuzz tests;
4. backend API contract and resilience tests;
5. React Native component/accessibility tests;
6. packaged Android/iOS end-to-end core journeys;
7. physical-device capability matrix;
8. performance, memory, battery, backup/restore, and soak tests;
9. staged-release monitoring and rollback checks.

Keep the current tests as characterization evidence, but delete tests for removed features and rewrite tests that only preserve accidental implementation shape.

### Release and observability

- Retain source gates, permission drift checks, exact lockfile/toolchain use, and candidate binding.
- Add iOS build verification and signed-artifact attestations.
- Require device evidence only for capabilities actually shipping.
- Gate rollout on crash-free health, migration success, core journey success, reminder reliability, and backend SLOs.
- Capture only explicitly opted-in, allowlisted telemetry using short-lived operation IDs. Never collect stable person/contact identifiers, names, routes, dates, relationship labels, message bodies, memory text, AI prompts/responses, tokens, backup metadata, or raw provider payloads.
- Give every alert and recovery runbook an owner.

## Migration strategy

### Decision gate: clean cut or strangler

Before implementation, answer one factual question: does any supported installed app contain user data that must survive?

#### If no production users/data exist: selective clean cut (preferred)

1. Freeze v1 except critical safety fixes.
2. Copy approved characterization tests into a v2 test plan; do not copy the monolith.
3. Implement v2 slices and UI behind a new composition root.
4. Reuse/adapt approved native ports and policies.
5. Remove the v1 command runtime once parity for the reduced MVP is proven.
6. Ship only after signed-device and production-service evidence exists.

#### If production data exists: strangler

1. Keep v1 storage read-only during migration.
2. Define canonical v2 schemas and a one-way, journaled migration.
3. Add compatibility readers for supported backups and installed data.
4. Route one vertical journey at a time to v2.
5. Compare v1/v2 derived outcomes in non-mutating shadow tests where safe.
6. Provide encrypted backup, migration verification, rollback window, and support tooling.
7. Remove v1 only after adoption, data parity, and rollback criteria are met.

Do not maintain two mutable sources of truth.

## Rebuild stages, risks, and exit criteria

The product roadmap owns phase numbering. Technical stages 0 and 1 correspond to product Phases 0 and 1; technical stages 2–7 decompose the narrow production work in product Phase 2. They do not replace the later habit/trust, monetization, or selective-expansion phases.

| Stage                         | Work                                                                                                                                                                                                                                            | Primary risks                                                                  | Exit criteria                                                                                                                                                                                             |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0. Product/evidence reset     | Interview target users; test the problem, segment, workaround, trust, recurrence, and willingness-to-pay hypotheses; confirm non-goals, live-user/data status, metrics, and platforms.                                                          | Rebuilding the wrong product; hidden data obligations.                         | Phase 0 interview and workaround evidence recorded; segment/problem either validated or repositioned; named owner; explicit live-data decision; feature kill list.                                        |
| 1. Figma experience proof     | Prototype Today, Moments, People, contextual Compose/Review, context preview, duplicate warning/handoff, permission denial, provider fallback, recovery, and export/delete explanation without using the command console as a design reference. | Polishing the old taxonomy; validating aesthetics instead of task completion.  | Representative users complete first value and reminder-to-handoff without coaching; trust/terminology is understood; critical accessibility and language review passes.                                   |
| 2. Architecture foundation    | Approve v2 ADRs, feature slices, value types, error model, clock, observability contract, and dev harness boundary.                                                                                                                             | Recreating abstractions without delivering value.                              | Dependency rules enforced; one thin vertical slice compiles end to end; no global v2 state/reducer/router.                                                                                                |
| 3. Data/security foundation   | Select encrypted storage, define schema/retention/deletion, backup compatibility, migration, threat model, and recovery.                                                                                                                        | Data loss; cryptographic regression; irreversible migration.                   | Independent review complete; corruption/interruption tests pass; real-device clear/restore/migration budgets pass; rollback demonstrated.                                                                 |
| 4. Core local journeys and UI | People, Moments, Today, reminders, local templates, Compose/Review, manual handoff, minimal context, privacy, export/delete, backup/restore, app lock, support/policy paths, loading/empty/error states, localization, and accessibility.       | Scope creep; visual design bypasses use cases; losing review-first edge cases. | Typed use cases and accessible UI complete; moderated validation and development-device core E2E, including reminder/handoff/restore/lock, pass on Android/iOS; no AI/backend dependency for first value. |
| 5. Optional backend/AI        | Build BFF, auth/session, API contract, quotas, evaluation, fallback, deletion, and telemetry.                                                                                                                                                   | Privacy, unsafe output, cost, latency, provider lock-in.                       | Security/privacy approval; contract/load tests; model-quality thresholds; fallback and kill switch; backend SLO dashboards.                                                                               |
| 6. Native/release hardening   | Signed builds, full device matrix, notification/biometric/share checks and any evidence-gated calendar support actually shipping, migration beta, store declarations, rollout/rollback.                                                         | OEM/platform failures; store rejection; migration incidents.                   | Clean candidate evidence; signed Android/iOS matrix and smokes; privacy/store signoff; rollback drill; monitored staged rollout.                                                                          |
| 7. Cutover and deletion       | Migrate supported data, monitor, remove v1 runtime/docs/dead features, close compatibility window deliberately.                                                                                                                                 | Premature removal; long-lived dual architecture.                               | Migration success threshold met; support window complete; no v1 writes; obsolete code/docs removed; ownership transferred.                                                                                |

## Cross-cutting risk register

| Risk                                                | Probability/impact | Mitigation                                                                                                         |
| --------------------------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------ |
| No validated customer problem                       | High/high          | Complete user research and define measurable core outcome before stage 1.                                          |
| Feature scope returns to the 28-area legacy breadth | High/high          | Enforce MVP kill list and require evidence/owner/metric for every addition.                                        |
| Existing safety behavior is lost                    | Medium/high        | Preserve characterization tests for approved rules and map each to v2 acceptance.                                  |
| Installed data is overlooked                        | Unknown/critical   | Inventory releases/users/backups before selecting clean cut.                                                       |
| Storage rewrite causes data loss                    | Medium/critical    | Independent review, journaled migration, backup, rollback, device interruption testing.                            |
| Custom storage remains without justification        | Medium/high        | Time-box comparison against reviewed encrypted alternatives using real-device benchmarks.                          |
| Backend becomes a secret relay only                 | Medium/high        | Define identity, authorization, idempotency, quotas, deletion, observability, and ownership before implementation. |
| AI costs or quality invalidate value                | High/high          | Ship local-template value first; run model evaluation and cost experiments; provide kill switch.                   |
| Android/iOS parity diverges                         | Medium/high        | Capability matrix, platform owners, signed-device CI/evidence, defer non-core platform-specific features.          |
| Figma UI couples directly to persistence            | Medium/high        | Enforce typed use-case/view-model boundary and component contracts.                                                |
| Test count preserves dead scope                     | High/medium        | Measure journey risk coverage; delete tests for intentionally removed behavior.                                    |
| Privacy-safe telemetry is omitted again             | Medium/high        | Approve event allowlist, consent/disclosure, redaction tests, SLOs, and dashboard ownership before beta.           |

## Final recommendation

RelateAI does not need a cosmetic UI rebuild over the current architecture. It needs a product reset and a selective technical rebuild.

The current codebase has more rigor than product evidence. It has encoded a broad imagined system—141 commands, complex encrypted storage, release provenance, analytics, diagnostics, widgets, bulk workflows, gift/style features—before proving the basic user loop or supplying the backend and UI required to deliver it. Continuing directly would turn sunk implementation effort into permanent architecture.

Preserve the difficult lessons already captured: review before send, duplicate prevention, explicit permissions, safe fallback, bounded external input, encrypted backup, crash recovery, and truthful capability reporting. Rebuild the rest around a sharply reduced product and typed vertical use cases.

The immediate next decisions are:

1. Confirm whether any real users or persisted data constrain migration.
2. Approve the reduced MVP and explicit removals.
3. Decide whether authenticated assisted composition passes its MVP gates; keep account-backed continuity as a separate later decision.
4. Commission the data/security architecture decision.
5. Choose clean-cut or strangler execution using the decision gate above.

Until those decisions and the missing production evidence exist, the honest status is: technically promising prototype, not production-ready product.
